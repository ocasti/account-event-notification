#!/usr/bin/env python3
"""Load test for the account-event-notification local stack.

Publishes N account events straight onto the SQS queue (ElasticMQ), a fraction of which are
made to fail every webhook delivery attempt via a temporary WireMock stub, then polls the API
until every event reaches a terminal delivery status (or the timeout is reached). Reports
whether any event was lost, whether completed/failed counts match what was published, whether
the API's attempt count matches what WireMock actually received, and the relevant Prometheus
deltas (registered, duplicates, deliveries by status) plus webhook p95 latency and attempts-due
backlog for the run window.

Runs entirely on the Python 3 standard library (urllib, json, threading via
concurrent.futures, argparse, uuid, datetime) so it can execute unmodified inside a bare
`python:3.12-alpine` container attached to the `account-event-notification` Docker network,
where it can reach `elasticmq:9324` (SQS query protocol), `http://notifications-api:8080`,
`http://wiremock:8080` and `http://prometheus:9090`. See `make load` in the repository
Makefile for how it is invoked against the running stack.

Exit code: 0 on RESULT: PASS, 1 on RESULT: FAIL or on a fatal setup error.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

SQS_URL = os.environ.get("SQS_URL", "http://elasticmq:9324/000000000000/account-events")
API_URL = os.environ.get("API_URL", "http://notifications-api:8080").rstrip("/")
WIREMOCK_URL = os.environ.get("WIREMOCK_URL", "http://wiremock:8080").rstrip("/")
PROMETHEUS_URL = os.environ.get("PROMETHEUS_URL", "http://prometheus:9090").rstrip("/")

CLIENTS = ["CLIENT001", "CLIENT002", "CLIENT003"]
EVENT_TYPES = ["credit_deposit", "debit_purchase", "credit_transfer"]
TERMINAL_STATUSES = {"completed", "failed"}
POLL_WORKERS = 16
POLL_INTERVAL_S = 2
BATCH_SIZE = 10


# --------------------------------------------------------------------------- HTTP helpers

def _urlopen(req, timeout):
    return urllib.request.urlopen(req, timeout=timeout)


def http_get_json(url, headers=None, timeout=10):
    """Returns (status, parsed_body_or_None). Never raises on HTTP error status."""
    req = urllib.request.Request(url, headers=headers or {}, method="GET")
    try:
        with _urlopen(req, timeout) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read()
        try:
            return e.code, json.loads(body.decode())
        except Exception:
            return e.code, None
    except Exception:
        return None, None


def http_post_json(url, payload, timeout=10):
    data = json.dumps(payload).encode()
    req = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/json"}
    )
    with _urlopen(req, timeout) as resp:
        body = resp.read()
        try:
            return resp.status, json.loads(body.decode())
        except Exception:
            return resp.status, None


def http_delete(url, timeout=10):
    req = urllib.request.Request(url, method="DELETE")
    with _urlopen(req, timeout) as resp:
        return resp.status


def http_post_form(url, fields, timeout=20):
    data = urllib.parse.urlencode(fields).encode()
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with _urlopen(req, timeout) as resp:
        return resp.status, resp.read().decode()


# --------------------------------------------------------------------------- WireMock

def wiremock_reset_journal():
    http_delete(f"{WIREMOCK_URL}/__admin/requests")


def wiremock_register_failure_stub(run):
    payload = {
        "name": f"load-{run}-fail",
        "priority": 1,
        "request": {
            "method": "POST",
            "urlPath": "/webhook",
            "bodyPatterns": [{"contains": f"\"LOAD-{run}-F-"}],
        },
        "response": {
            "status": 503,
            "headers": {"Content-Type": "application/json"},
            "jsonBody": {"error": "load test induced failure", "run": run},
        },
    }
    status, body = http_post_json(f"{WIREMOCK_URL}/__admin/mappings", payload)
    if status not in (200, 201) or not body or "id" not in body:
        raise RuntimeError(f"could not register WireMock failure stub: status={status} body={body}")
    return body["id"]


def wiremock_delete_mapping(mapping_id):
    try:
        http_delete(f"{WIREMOCK_URL}/__admin/mappings/{mapping_id}")
    except Exception as e:
        print(f"WARN: could not delete WireMock mapping {mapping_id}: {e}", file=sys.stderr)


def wiremock_post_count(run):
    payload = {
        "method": "POST",
        "url": "/webhook",
        "bodyPatterns": [{"contains": f"\"LOAD-{run}-"}],
    }
    _, body = http_post_json(f"{WIREMOCK_URL}/__admin/requests/count", payload)
    if not body:
        return None
    return body.get("count")


# --------------------------------------------------------------------------- Prometheus

def prom_query(query):
    url = f"{PROMETHEUS_URL}/api/v1/query?" + urllib.parse.urlencode({"query": query})
    status, body = http_get_json(url)
    if status != 200 or not body or body.get("status") != "success":
        return []
    return body["data"]["result"]


def prom_scalar_sum(query):
    result = prom_query(query)
    total = 0.0
    for r in result:
        try:
            total += float(r["value"][1])
        except (KeyError, ValueError, TypeError):
            pass
    return total


def prom_by_label(query, label):
    result = prom_query(query)
    out = {}
    for r in result:
        key = r.get("metric", {}).get(label, "unknown")
        try:
            out[key] = out.get(key, 0.0) + float(r["value"][1])
        except (KeyError, ValueError, TypeError):
            pass
    return out


def prom_deliveries_snapshot():
    return prom_by_label("sum by (status) (notifications_deliveries_total)", "status")


def prom_range_max(query, start_ts, end_ts, step_s=15):
    if end_ts <= start_ts:
        end_ts = start_ts + step_s
    url = f"{PROMETHEUS_URL}/api/v1/query_range?" + urllib.parse.urlencode(
        {"query": query, "start": start_ts, "end": end_ts, "step": f"{step_s}s"}
    )
    status, body = http_get_json(url)
    if status != 200 or not body or body.get("status") != "success":
        return None
    values = []
    for series in body["data"]["result"]:
        for _, raw in series.get("values", []):
            try:
                v = float(raw)
                if v == v:  # filters NaN
                    values.append(v)
            except (ValueError, TypeError):
                pass
    return max(values) if values else None


# --------------------------------------------------------------------------- SQS publish

def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def build_events(n, fail_ratio, run):
    """Returns list of dicts: event_id, client_id, event_type, is_failing."""
    fail_count = round(n * fail_ratio)
    fail_indices = set()
    if fail_count > 0:
        for k in range(fail_count):
            fail_indices.add((k * n) // fail_count)

    events = []
    for i in range(n):
        client = CLIENTS[i % len(CLIENTS)]
        event_type = EVENT_TYPES[i % len(EVENT_TYPES)]
        is_failing = i in fail_indices
        event_id = f"LOAD-{run}-F-{i}" if is_failing else f"LOAD-{run}-{i}"
        events.append(
            {
                "event_id": event_id,
                "client_id": client,
                "event_type": event_type,
                "is_failing": is_failing,
            }
        )
    return events


def publish_batch(batch):
    """Publishes one SendMessageBatch (<=10 entries). Returns (sent_count, error_or_None)."""
    fields = {"Action": "SendMessageBatch", "Version": "2012-11-05"}
    for idx, ev in enumerate(batch, start=1):
        body = json.dumps(
            {
                "event_id": ev["event_id"],
                "event_type": ev["event_type"],
                "client_id": ev["client_id"],
                "content": f"load test event ({ev['event_type']} for {ev['client_id']})",
                "occurred_at": now_iso(),
            }
        )
        fields[f"SendMessageBatchRequestEntry.{idx}.Id"] = str(idx)
        fields[f"SendMessageBatchRequestEntry.{idx}.MessageBody"] = body

    last_err = None
    for attempt in range(3):
        try:
            status, text = http_post_form(SQS_URL, fields)
            if status == 200:
                error_entries = text.count("<BatchResultErrorEntry>")
                sent = len(batch) - error_entries
                return sent, (f"{error_entries} entries rejected by SQS" if error_entries else None)
            last_err = f"SendMessageBatch returned HTTP {status}"
        except Exception as e:
            last_err = str(e)
        time.sleep(0.5 * (attempt + 1))
    return 0, last_err


def publish_all(events, concurrency):
    batches = [events[i : i + BATCH_SIZE] for i in range(0, len(events), BATCH_SIZE)]
    sent_total = 0
    errors = []
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(publish_batch, b) for b in batches]
        for fut in as_completed(futures):
            sent, err = fut.result()
            sent_total += sent
            if err:
                errors.append(err)
    return sent_total, errors


# --------------------------------------------------------------------------- API polling

def fetch_event_status(event_id, client_id, token):
    url = f"{API_URL}/notification_events/{urllib.parse.quote(event_id, safe='')}"
    status, body = http_get_json(url, headers={"Authorization": f"Bearer {token}"})
    if status == 200 and body:
        return "found", body.get("delivery_status"), body.get("attempts_count", 0)
    if status == 404:
        return "not_found", None, None
    return "error", None, None


def wait_for_terminal(events, tokens, timeout_s):
    ids = [ev["event_id"] for ev in events]
    id_to_client = {ev["event_id"]: ev["client_id"] for ev in events}
    n = len(ids)

    pending_ids = set(ids)  # not yet confirmed terminal
    found_ever = set()
    final_state = {}  # event_id -> (delivery_status, attempts_count)

    start = time.time()
    round_no = 0
    with ThreadPoolExecutor(max_workers=POLL_WORKERS) as pool:
        while True:
            round_no += 1
            elapsed = time.time() - start
            to_check = list(pending_ids)
            if not to_check:
                break
            if elapsed > timeout_s:
                break

            futures = {
                pool.submit(
                    fetch_event_status, eid, id_to_client[eid], tokens[id_to_client[eid]]
                ): eid
                for eid in to_check
            }
            for fut in as_completed(futures):
                eid = futures[fut]
                kind, status_, attempts = fut.result()
                if kind == "found":
                    found_ever.add(eid)
                    final_state[eid] = (status_, attempts)
                    if status_ in TERMINAL_STATUSES:
                        pending_ids.discard(eid)
                # not_found / error: stays in pending_ids, retried next round

            completed_n = sum(1 for s, _ in final_state.values() if s == "completed")
            failed_n = sum(1 for s, _ in final_state.values() if s == "failed")
            stuck_n = len(found_ever) - completed_n - failed_n
            print(
                f"  [{elapsed:5.0f}s] registrados {len(found_ever)}/{n}, "
                f"completed {completed_n}, failed {failed_n}, pendientes {stuck_n + (n - len(found_ever))}",
                flush=True,
            )

            if pending_ids:
                time.sleep(POLL_INTERVAL_S)

    duration = time.time() - start
    lost_ids = [eid for eid in ids if eid not in found_ever]
    return final_state, lost_ids, duration


# --------------------------------------------------------------------------- Main

def load_tokens():
    tokens = {}
    missing = []
    for client in CLIENTS:
        env_name = f"TOKEN_{client}"
        value = os.environ.get(env_name)
        if not value:
            missing.append(env_name)
        else:
            tokens[client] = value
    if missing:
        print(
            "ERROR: missing bearer tokens in the environment: " + ", ".join(missing) + "\n"
            "       (the Makefile 'load' target generates these with scripts/token.sh; "
            "if running the script directly, export them yourself, e.g.\n"
            "       TOKEN_CLIENT001=$(scripts/token.sh CLIENT001) ... )",
            file=sys.stderr,
        )
        sys.exit(1)
    return tokens


def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--events", type=int, default=2000, help="number of events to publish (default 2000)")
    p.add_argument("--fail-ratio", type=float, default=0.10, help="fraction that must fail every attempt (default 0.10)")
    p.add_argument("--concurrency", type=int, default=8, help="publishing threads, batches of 10 (default 8)")
    p.add_argument("--timeout", type=int, default=300, help="seconds to wait for terminal status (default 300)")
    p.add_argument("--run", default=uuid.uuid4().hex[:8], help="run tag, used to namespace event ids (default: random hex)")
    return p.parse_args()


def main():
    args = parse_args()
    if args.events <= 0:
        print("ERROR: --events must be > 0", file=sys.stderr)
        sys.exit(1)
    if not (0.0 <= args.fail_ratio <= 1.0):
        print("ERROR: --fail-ratio must be between 0 and 1", file=sys.stderr)
        sys.exit(1)

    run = args.run
    tokens = load_tokens()

    print(f"=== load_test run={run} events={args.events} fail_ratio={args.fail_ratio} "
          f"concurrency={args.concurrency} timeout={args.timeout}s ===")

    events = build_events(args.events, args.fail_ratio, run)
    expected_fail_ids = {ev["event_id"] for ev in events if ev["is_failing"]}
    expected_ok_ids = {ev["event_id"] for ev in events if not ev["is_failing"]}

    print("Snapshot inicial de Prometheus...")
    prom_before = {
        "registered": prom_scalar_sum("sum(notifications_registered_total)"),
        "duplicates": prom_scalar_sum("sum(notifications_duplicates_total)"),
        "deliveries": prom_deliveries_snapshot(),
    }

    print("Reseteando journal de WireMock y registrando stub de fallo...")
    wiremock_reset_journal()
    mapping_id = wiremock_register_failure_stub(run)

    exit_code = 1
    try:
        run_start_ts = int(time.time())

        print(f"Publicando {args.events} eventos ({args.concurrency} hilos, lotes de {BATCH_SIZE})...")
        publish_t0 = time.time()
        sent_total, publish_errors = publish_all(events, args.concurrency)
        publish_duration = time.time() - publish_t0
        throughput_publish = sent_total / publish_duration if publish_duration > 0 else 0.0
        print(f"  publicados {sent_total}/{args.events} en {publish_duration:.2f}s "
              f"({throughput_publish:.1f} msg/s)")
        if publish_errors:
            print(f"  WARN: {len(publish_errors)} errores durante la publicacion (primeros 5): "
                  f"{publish_errors[:5]}")

        print(f"Esperando estado terminal (timeout {args.timeout}s)...")
        final_state, lost_ids, wait_duration = wait_for_terminal(events, tokens, args.timeout)

        run_end_ts = int(time.time())
        total_duration = run_end_ts - run_start_ts

        # ---- derive outcome sets
        registered_ids = set(final_state.keys())
        completed_ids = {eid for eid, (s, _) in final_state.items() if s == "completed"}
        failed_ids = {eid for eid, (s, _) in final_state.items() if s == "failed"}
        stuck_ids = registered_ids - completed_ids - failed_ids

        expected_completed_n = len(expected_ok_ids)
        expected_failed_n = len(expected_fail_ids)

        unexpected_failures = failed_ids - expected_fail_ids
        expected_but_not_failed = expected_fail_ids - failed_ids
        failed_with_wrong_attempts = {
            eid for eid in failed_ids if final_state[eid][1] != 5
        }

        api_attempts_total = sum(a or 0 for _, a in final_state.values())
        wiremock_count = wiremock_post_count(run)

        prom_after = {
            "registered": prom_scalar_sum("sum(notifications_registered_total)"),
            "duplicates": prom_scalar_sum("sum(notifications_duplicates_total)"),
            "deliveries": prom_deliveries_snapshot(),
        }
        duplicates_delta = prom_after["duplicates"] - prom_before["duplicates"]
        registered_delta = prom_after["registered"] - prom_before["registered"]
        deliveries_delta = {
            status: prom_after["deliveries"].get(status, 0.0) - prom_before["deliveries"].get(status, 0.0)
            for status in set(prom_after["deliveries"]) | set(prom_before["deliveries"])
        }

        p95_latency = prom_range_max(
            "histogram_quantile(0.95, sum by (le) (rate(notifications_webhook_latency_seconds_bucket[1m])))",
            run_start_ts,
            run_end_ts,
        )
        max_attempts_due = prom_range_max("max(notifications_attempts_due)", run_start_ts, run_end_ts)

        delivered_n = len(completed_ids) + len(failed_ids)
        throughput_deliveries = delivered_n / wait_duration if wait_duration > 0 else 0.0

        # ---- report
        print()
        print("=" * 78)
        print("INFORME")
        print("=" * 78)
        print(f"Publicados:            {sent_total}/{args.events} en {publish_duration:.2f}s "
              f"({throughput_publish:.1f} msg/s)")
        print(f"Registrados (API):     {len(registered_ids)}/{args.events}")
        print(f"Perdidos (404 al final): {len(lost_ids)}")
        if lost_ids:
            if len(lost_ids) <= 20:
                print(f"  ids perdidos: {lost_ids}")
            else:
                print(f"  (mas de 20, no se listan; primeros 20: {lost_ids[:20]})")
        print(f"Completed:             esperados {expected_completed_n}, obtenidos {len(completed_ids)}")
        print(f"Failed:                esperados {expected_failed_n}, obtenidos {len(failed_ids)}")
        if unexpected_failures:
            sample = list(unexpected_failures)[:10]
            print(f"  WARN: {len(unexpected_failures)} ids fallaron sin deber hacerlo (ej: {sample})")
        if expected_but_not_failed:
            sample = list(expected_but_not_failed)[:10]
            print(f"  WARN: {len(expected_but_not_failed)} ids -F- no terminaron failed (ej: {sample})")
        if failed_with_wrong_attempts:
            sample = list(failed_with_wrong_attempts)[:10]
            print(f"  WARN: {len(failed_with_wrong_attempts)} failed con attempts_count != 5 (ej: {sample})")
        if stuck_ids:
            sample = list(stuck_ids)[:10]
            print(f"  WARN: {len(stuck_ids)} ids registrados pero sin estado terminal al timeout (ej: {sample})")
        print(f"Duracion total:        {total_duration}s, throughput entregas: {throughput_deliveries:.2f}/s "
              f"(completed+failed={delivered_n})")
        print(f"Intentos API (suma attempts_count): {api_attempts_total}")
        print(f"POST /webhook en WireMock (filtrado por run={run}): {wiremock_count}")
        if wiremock_count is None:
            print("  WARN: no se pudo leer /__admin/requests/count de WireMock")
        print(f"Prometheus delta registered: {registered_delta:+.0f}")
        print(f"Prometheus delta duplicates: {duplicates_delta:+.0f}")
        print("Prometheus delta deliveries por status:")
        for status in sorted(deliveries_delta):
            print(f"  {status}: {deliveries_delta[status]:+.0f}")
        print(f"p95 webhook latency (ventana de la corrida): "
              f"{'%.3fs' % p95_latency if p95_latency is not None else 'sin datos'}")
        print(f"max(notifications_attempts_due) (ventana de la corrida): "
              f"{max_attempts_due if max_attempts_due is not None else 'sin datos'}")

        # ---- PASS/FAIL
        checks = {
            "0 perdidos": len(lost_ids) == 0,
            "completed == esperados": len(completed_ids) == expected_completed_n,
            "failed == esperados (ids exactos, 5 intentos)": (
                len(failed_ids) == expected_failed_n
                and not unexpected_failures
                and not expected_but_not_failed
                and not failed_with_wrong_attempts
            ),
            "intentos API == POST WireMock": (
                wiremock_count is not None and api_attempts_total == wiremock_count
            ),
            "duplicados delta == 0": duplicates_delta == 0,
        }
        print()
        print("Criterios:")
        overall_pass = True
        for name, ok in checks.items():
            overall_pass = overall_pass and ok
            print(f"  [{'OK' if ok else 'FAIL'}] {name}")

        print()
        if overall_pass:
            print("RESULT: PASS")
            exit_code = 0
        else:
            print("RESULT: FAIL")
            exit_code = 1

    finally:
        print()
        print("Borrando stub de fallo de WireMock...")
        wiremock_delete_mapping(mapping_id)

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
