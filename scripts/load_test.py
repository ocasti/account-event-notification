#!/usr/bin/env python3
"""Load test for the account-event-notification local stack.

Publishes N account events straight onto the SQS queue (ElasticMQ), a fraction of which are
made to fail every webhook delivery attempt via a temporary WireMock stub, then waits for the
backlog to drain and reports whether any event was lost, whether completed/failed counts match
what was published, whether the receiving side saw the attempts the API says it made, and the
relevant Prometheus deltas (registered, duplicates, deliveries by status) plus webhook p95
latency and attempts-due backlog for the run window.

The wait phase does NOT poll every event id every round. Instead, each round it makes 6 cheap
listing requests (`GET /notification_events?delivery_status=pending&limit=1` and
`...=retrying&limit=1`, once per client) and considers the system drained once two consecutive
rounds come back empty for all three clients — a single empty round is not enough because the
background event simulator emits roughly one event every 2s, which can appear as `pending` for
only a few milliseconds. This keeps the harness itself from competing on CPU with the service
under test and with Prometheus/Grafana during a large run. Only once the system is drained (or
the wait timeout is reached) does the script make one single pass of `GET
/notification_events/{id}` per id, at rest, to classify everyone's final status; any id still
non-terminal after that pass gets up to 3 more passes, 5s apart, before being reported stuck.

Receiver check has two variants, chosen automatically. WireMock's request journal only holds
`--wiremock-journal-limit` entries (default 5000, matching `compose.yaml`); a larger journal
stalls WireMock because it scans the whole journal on every request. When the run's expected
POST count fits the journal, the check compares the API's summed `attempts_count` against
WireMock's own `POST /webhook` count for this run (variant A). When it does not, the check
instead reads the `attempts` list from the final classification pass and verifies that no
attempt has a null `response_status` (an I/O error: timeout or connection failure — the
attempt never reached the receiver) and that every `-F-` id's attempts all got 503 from the
stub (variant B). Both attempt counts (numeric vs. null `response_status`) are always printed
regardless of which variant gates PASS.

While the drain wait runs, a second, independent load driver keeps hitting the self-service
REST API (`--api-rps`, `--api-clients` threads) with a realistic mix of listing (cursor
pagination and filtered), detail, replay and negative-auth requests, cycling through the three
clients' tokens. This exists because the wait phase's own probe barely touches the API (6
cheap requests every 5s) — without it, `notifications-api`'s own HTTP surface (the "API HTTP"
row in Grafana, per-route latency percentiles) gets no real traffic during a run. It stops the
moment the drain wait ends and never counts toward the drain probe itself. Replays add new
delivery cycles to the ids they hit, so `expected_posts` (and therefore the variant A/B choice)
and the "5 attempts" check both account for however many replays actually landed.

Runs entirely on the Python 3 standard library (urllib, json, threading via
concurrent.futures and threading.Thread, argparse, uuid, random, datetime) so it can execute
unmodified inside a bare `python:3.12-alpine` container attached to the
`account-event-notification` Docker network, where it can reach `elasticmq:9324` (SQS query
protocol), `http://notifications-api:8080`, `http://wiremock:8080` and
`http://prometheus:9090`. See `make load` in the repository Makefile for how it is invoked
against the running stack.

Exit code: 0 on RESULT: PASS, 1 on RESULT: FAIL or on a fatal setup error.
"""
from __future__ import annotations

import argparse
import json
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone

SQS_URL = os.environ.get("SQS_URL", "http://elasticmq:9324/000000000000/account-events")
API_URL = os.environ.get("API_URL", "http://notifications-api:8080").rstrip("/")
WIREMOCK_URL = os.environ.get("WIREMOCK_URL", "http://wiremock:8080").rstrip("/")
PROMETHEUS_URL = os.environ.get("PROMETHEUS_URL", "http://prometheus:9090").rstrip("/")

CLIENTS = ["CLIENT001", "CLIENT002", "CLIENT003"]
EVENT_TYPES = ["credit_deposit", "debit_purchase", "credit_transfer"]
TERMINAL_STATUSES = {"completed", "failed"}
LISTING_STATUSES = ("pending", "retrying")
POLL_INTERVAL_S = 5
DRAIN_CONFIRM_ROUNDS = 2  # consecutive empty rounds required before declaring the system drained
FINAL_CLASSIFY_RETRIES = 3  # extra passes over ids still non-terminal after the final pass
FINAL_CLASSIFY_RETRY_INTERVAL_S = 5
DEFAULT_POLL_CONCURRENCY = 8
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


def timed_get_json(url, token=None, timeout=8):
    """GET with client-measured latency. Returns (status_or_None, elapsed_seconds, body_or_None).
    Never raises: network errors and non-2xx responses are reported via the return value so the
    REST load generator can keep going and record whatever happened."""
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    req = urllib.request.Request(url, method="GET", headers=headers)
    t0 = time.time()
    try:
        with _urlopen(req, timeout) as resp:
            body = resp.read()
            elapsed = time.time() - t0
            try:
                return resp.status, elapsed, json.loads(body.decode())
            except Exception:
                return resp.status, elapsed, None
    except urllib.error.HTTPError as e:
        elapsed = time.time() - t0
        try:
            return e.code, elapsed, json.loads(e.read().decode())
        except Exception:
            return e.code, elapsed, None
    except Exception:
        return None, time.time() - t0, None


def timed_post(url, token=None, timeout=8):
    """POST with no body, client-measured latency. Returns (status_or_None, elapsed_seconds)."""
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    req = urllib.request.Request(url, method="POST", headers=headers)
    t0 = time.time()
    try:
        with _urlopen(req, timeout) as resp:
            resp.read()
            return resp.status, time.time() - t0
    except urllib.error.HTTPError as e:
        e.read()
        return e.code, time.time() - t0
    except Exception:
        return None, time.time() - t0


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
    """Returns (kind, delivery_status_or_None, attempts_count_or_None, attempts_list).

    attempts_list is the raw `attempts` array from the detail response (each item carries
    `cycle`, `response_status` — null on an I/O error such as a timeout or connection failure
    — among other fields); used by the WireMock-journal-independent receiver check (variant B)
    and by the "5 attempts" check once replays are in play.
    """
    url = f"{API_URL}/notification_events/{urllib.parse.quote(event_id, safe='')}"
    status, body = http_get_json(url, headers={"Authorization": f"Bearer {token}"})
    if status == 200 and body:
        return "found", body.get("delivery_status"), body.get("attempts_count", 0), body.get("attempts", [])
    if status == 404:
        return "not_found", None, None, []
    return "error", None, None, []


def fetch_listing_page(client, token, delivery_status):
    """GET /notification_events?delivery_status=...&limit=50 for one client.

    Returns (ok, items). ok is False on any non-200 response or network error, in which
    case the caller must treat the client as "not confirmed empty" (i.e. keep polling)
    rather than assume it drained.
    """
    params = urllib.parse.urlencode({"delivery_status": delivery_status, "limit": 50})
    url = f"{API_URL}/notification_events?{params}"
    status, body = http_get_json(url, headers={"Authorization": f"Bearer {token}"})
    if status != 200 or body is None:
        return False, None
    return True, body.get("items", [])


def all_clients_drained(tokens, run_prefix):
    """One round of the aggregated poll: 6 cheap listing requests (pending + retrying,
    per client), run concurrently. True only if no listing returned an item of this run
    (ids starting with run_prefix); events of the background simulator are ignored, since
    they sit in pending for a few milliseconds every two seconds and are not part of the run."""
    pairs = [(client, delivery_status) for client in CLIENTS for delivery_status in LISTING_STATUSES]
    with ThreadPoolExecutor(max_workers=len(pairs)) as pool:
        futures = [
            pool.submit(fetch_listing_page, client, tokens[client], delivery_status)
            for client, delivery_status in pairs
        ]
        drained = True
        for fut in futures:
            ok, items = fut.result()
            if not ok or any(str(it.get("event_id", "")).startswith(run_prefix) for it in items):
                drained = False
        return drained


def wait_for_drain(tokens, timeout_s, deliveries_before, events, api_rps, api_clients):
    """Polls the pending/retrying listings every POLL_INTERVAL_S until DRAIN_CONFIRM_ROUNDS
    consecutive rounds come back empty for all clients, or until timeout_s elapses. Prints one
    progress line per round (Prometheus attempts-due backlog and the deliveries-by-status delta
    since the run started) without touching the per-id detail endpoint.

    For the duration of this wait (and only for this duration), also runs the REST load
    generator against the self-service API if api_rps > 0 — started right before the polling
    loop and always stopped (stop_rest_load, via finally) before returning, on every exit path,
    so it never overlaps with the drain probe or with the final classification pass.

    Returns (drained: bool, elapsed_seconds: float, rest_stats: RestLoadStats).
    """
    run_prefix = events[0]["event_id"].split("-")[0] + "-" + events[0]["event_id"].split("-")[1] + "-"
    print(
        "  (per-round progress via aggregated listing; deliveries include background "
        "traffic from the event simulator, which keeps emitting during the run)"
    )
    if api_rps > 0:
        print(f"  (concurrent REST load: ~{api_rps} req/s across {api_clients} threads, "
              f"stopping together with the drain)")
    else:
        print("  (concurrent REST load disabled: --api-rps 0)")

    rest_stats, rest_stop_event, rest_threads = start_rest_load(events, tokens, api_rps, api_clients)
    try:
        start = time.time()
        consecutive_empty = 0
        while True:
            elapsed = time.time() - start
            if elapsed > timeout_s:
                return False, elapsed, rest_stats

            empty_round = all_clients_drained(tokens, run_prefix)
            consecutive_empty = consecutive_empty + 1 if empty_round else 0

            backlog = prom_scalar_sum("max(notifications_attempts_due)")
            deliveries_now = prom_deliveries_snapshot()
            deliveries_delta = {
                status: deliveries_now.get(status, 0.0) - deliveries_before.get(status, 0.0)
                for status in sorted(set(deliveries_now) | set(deliveries_before))
            }
            delta_str = ", ".join(f"{status} {delta:+.0f}" for status, delta in deliveries_delta.items())
            print(
                f"  [{elapsed:5.0f}s] overdue backlog {backlog:.0f} · "
                f"deliveries since start: {delta_str}",
                flush=True,
            )

            if consecutive_empty >= DRAIN_CONFIRM_ROUNDS:
                return True, elapsed, rest_stats

            time.sleep(POLL_INTERVAL_S)
    finally:
        stop_rest_load(rest_stop_event, rest_threads)


def classify_pass(ids, id_to_client, tokens, concurrency):
    """One GET per id, in parallel. Returns dict event_id -> (kind, status, attempts_count, attempts_list)."""
    results = {}
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = {
            pool.submit(fetch_event_status, eid, id_to_client[eid], tokens[id_to_client[eid]]): eid
            for eid in ids
        }
        for fut in as_completed(futures):
            eid = futures[fut]
            results[eid] = fut.result()
    return results


def classify_all(events, tokens, concurrency):
    """Single pass of GET /notification_events/{id} over every id, done once the system is
    at rest (drained or timed out). Ids still non-terminal after that single pass get up to
    FINAL_CLASSIFY_RETRIES more passes, FINAL_CLASSIFY_RETRY_INTERVAL_S apart, before being
    reported as stuck. 404s from the first pass are reported as lost right away — they are
    not retried, since the aggregated wait already gave the system time to settle.

    Returns (final_state, lost_ids, stuck_ids):
      - final_state: event_id -> (delivery_status, attempts_count, attempts_list) for every id
        ever found
      - lost_ids: ids that came back 404 (or errored) on the first pass
      - stuck_ids: ids found but still non-terminal after all passes
    """
    ids = [ev["event_id"] for ev in events]
    id_to_client = {ev["event_id"]: ev["client_id"] for ev in events}

    results = classify_pass(ids, id_to_client, tokens, concurrency)

    final_state = {}
    lost_ids = []
    non_terminal_ids = []
    for eid in ids:
        kind, status_, attempts_count, attempts_list = results[eid]
        if kind == "found":
            final_state[eid] = (status_, attempts_count, attempts_list)
            if status_ not in TERMINAL_STATUSES:
                non_terminal_ids.append(eid)
        else:
            lost_ids.append(eid)

    extra_pass = 0
    while non_terminal_ids and extra_pass < FINAL_CLASSIFY_RETRIES:
        time.sleep(FINAL_CLASSIFY_RETRY_INTERVAL_S)
        extra_pass += 1
        results = classify_pass(non_terminal_ids, id_to_client, tokens, concurrency)
        still_pending = []
        for eid in non_terminal_ids:
            kind, status_, attempts_count, attempts_list = results[eid]
            if kind == "found":
                final_state[eid] = (status_, attempts_count, attempts_list)
                if status_ not in TERMINAL_STATUSES:
                    still_pending.append(eid)
            else:
                still_pending.append(eid)
        non_terminal_ids = still_pending

    return final_state, lost_ids, non_terminal_ids


# --------------------------------------------------------------------------- REST load generator
#
# Keeps the self-service REST API under load while wait_for_drain polls, so notifications-api's
# own HTTP surface (Grafana's "API HTTP" row, per-route latency percentiles) has real traffic to
# show during a run instead of the wait phase's own 6-requests-per-5s probe. Runs on its own
# threads/tokens, entirely separate from that probe.

REST_BUCKETS = (
    "listing_cursor",
    "listing_filtered",
    "detail",
    "replay",
    "negative_no_token",
    "negative_cross_client",
)


class RestLoadStats:
    """Thread-safe counters for the REST load generator."""

    def __init__(self):
        self._lock = threading.Lock()
        self.latencies = {b: [] for b in REST_BUCKETS}
        self.status_counts = {b: {} for b in REST_BUCKETS}
        self.unexpected_count = {b: 0 for b in REST_BUCKETS}
        self.unexpected_samples = {b: [] for b in REST_BUCKETS}
        self.five_xx_count = 0
        self.replays_launched = 0

    def record(self, bucket, status, elapsed, expected_statuses):
        with self._lock:
            self.latencies[bucket].append(elapsed)
            self.status_counts[bucket][status] = self.status_counts[bucket].get(status, 0) + 1
            if status is not None and 500 <= status < 600:
                self.five_xx_count += 1
            if status not in expected_statuses:
                self.unexpected_count[bucket] += 1
                if len(self.unexpected_samples[bucket]) < 10:
                    self.unexpected_samples[bucket].append(status)

    def note_replay(self):
        with self._lock:
            self.replays_launched += 1

    def total_unexpected(self):
        return sum(self.unexpected_count.values())

    def total_requests(self):
        return sum(len(v) for v in self.latencies.values())


class FailedIdPool:
    """Thread-safe pool of (event_id, owning_client) pairs observed as delivery_status ==
    failed, fed by the listing-filtered and detail buckets and drained by the replay bucket.

    Stores the owning client alongside each id — not just the id — because the listing and
    detail buckets can surface failed ids that are NOT part of this run's own event set (the
    background event simulator keeps emitting in parallel, and a failed id from an earlier run
    may still be sitting there); there is no `id_to_client` entry for those, but the client
    whose token fetched the listing/detail response is, by the API's own client-scoped
    authorization, necessarily its owner. Popping removes the entry so the same id is not
    replayed twice: one replay per id per run, so the run reaches a terminal state."""

    def __init__(self):
        self._lock = threading.Lock()
        self._entries = []  # list of (event_id, client)
        self._seen_ids = set()

    def add_many(self, ids, client):
        if not ids:
            return
        with self._lock:
            for i in ids:
                if i not in self._seen_ids:
                    self._seen_ids.add(i)
                    self._entries.append((i, client))

    def pop_random(self):
        with self._lock:
            if not self._entries:
                return None
            idx = random.randrange(len(self._entries))
            eid, client = self._entries.pop(idx)
            # _seen_ids is deliberately NOT cleared: each id is replayed at most once per run,
            # otherwise every replay cycle that ends failed is replayed again and the run never
            # reaches a terminal state.
            return eid, client


def _rest_do_listing_cursor(token, stats):
    """40% bucket: GET ?limit=50, then follow next_cursor up to 3 pages. Each page is its own
    recorded request."""
    cursor = None
    for _ in range(3):
        params = {"limit": 50}
        if cursor:
            params["cursor"] = cursor
        url = f"{API_URL}/notification_events?" + urllib.parse.urlencode(params)
        status, elapsed, body = timed_get_json(url, token)
        stats.record("listing_cursor", status, elapsed, {200})
        if status != 200 or not body:
            break
        cursor = body.get("next_cursor")
        if not cursor:
            break


def _rest_do_listing_filtered(token, client, stats, failed_pool, own_fail_ids):
    """20% bucket: GET filtered by delivery_status (failed/completed) and a last-hour from/to
    window. Failed ids seen here feed the replay bucket's pool, tagged with the client whose
    token fetched them (the API already scopes the listing to that client) — but only the ones
    that are this run's own induced `-F-` ids: the listing is not scoped to the run, so a
    `delivery_status=failed` page can just as easily surface unrelated failed events already
    sitting in the system (the reference dataset's EVT003/EVT005/EVT009, or leftovers from an
    earlier run), which the replay bucket must not touch."""
    delivery_status = random.choice(["failed", "completed"])
    now = datetime.now(timezone.utc)
    window_from = now - timedelta(hours=1)
    fmt = lambda dt: dt.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
    params = {
        "delivery_status": delivery_status,
        "limit": 50,
        "from": fmt(window_from),
        "to": fmt(now),
    }
    url = f"{API_URL}/notification_events?" + urllib.parse.urlencode(params)
    status, elapsed, body = timed_get_json(url, token)
    stats.record("listing_filtered", status, elapsed, {200})
    if status == 200 and body and delivery_status == "failed":
        ids = [item.get("event_id") for item in body.get("items", []) if item.get("event_id")]
        failed_pool.add_many([i for i in ids if i in own_fail_ids], client)


def _rest_do_detail(token, client, event_id, stats, failed_pool, own_fail_ids):
    """25% bucket (also the replay-bucket fallback when its pool is empty): GET one id of the
    run. 404 is tolerated here too — the event may not be registered yet (published to SQS but
    not yet consumed) this early in the run, which is not a defect. Only feeds the replay pool
    when the id is one of this run's own induced `-F-` ids (see _rest_do_listing_filtered)."""
    url = f"{API_URL}/notification_events/{urllib.parse.quote(event_id, safe='')}"
    status, elapsed, body = timed_get_json(url, token)
    stats.record("detail", status, elapsed, {200, 404})
    if status == 200 and body and body.get("delivery_status") == "failed" and event_id in own_fail_ids:
        failed_pool.add_many([event_id], client)


def _rest_do_replay(eid, token, stats):
    """10% bucket: replay an id already known to be failed. Expects 202."""
    url = f"{API_URL}/notification_events/{urllib.parse.quote(eid, safe='')}/replay"
    status, elapsed = timed_post(url, token)
    # 409 is a legitimate answer: the event is not in a replayable state yet (its cycle is running).
    stats.record("replay", status, elapsed, {202, 409})
    if status == 202:
        stats.note_replay()


def _rest_do_negative_no_token(stats):
    """Half of the 5% negatives bucket: no Authorization header. Expects 401."""
    url = f"{API_URL}/notification_events?limit=1"
    status, elapsed, _ = timed_get_json(url, token=None)
    stats.record("negative_no_token", status, elapsed, {401})


def _rest_do_negative_cross_client(event_id, wrong_token, stats):
    """Half of the 5% negatives bucket: detail of an id that belongs to a different client than
    the token used. Expects 404 (the API scopes lookups by authenticated client, not 403)."""
    url = f"{API_URL}/notification_events/{urllib.parse.quote(event_id, safe='')}"
    status, elapsed, _ = timed_get_json(url, wrong_token)
    stats.record("negative_cross_client", status, elapsed, {404})


MAX_REPLAYS = 200  # overridden by --max-replays; bounds the failure tail a run has to wait for


def rest_load_worker(stop_event, tokens, all_ids, id_to_client, own_fail_ids, failed_pool, stats, target_interval):
    """One REST load generator thread. Loops until stop_event is set, picking a request type per
    the documented mix and pacing itself to ~target_interval seconds between iterations so that
    all --api-clients threads together average ~--api-rps requests/s."""
    client_cycle = list(CLIENTS)
    i = 0
    while not stop_event.is_set():
        t0 = time.time()
        client = client_cycle[i % len(client_cycle)]
        i += 1
        token = tokens[client]
        r = random.random()
        if r < 0.40:
            _rest_do_listing_cursor(token, stats)
        elif r < 0.60:
            _rest_do_listing_filtered(token, client, stats, failed_pool, own_fail_ids)
        elif r < 0.85:
            _rest_do_detail(token, client, random.choice(all_ids), stats, failed_pool, own_fail_ids)
        elif r < 0.95:
            popped = failed_pool.pop_random() if stats.replays_launched < MAX_REPLAYS else None
            if popped is None:
                # nothing known-failed yet (early in the run); keep the rate up with a detail
                # request instead, tagged under "detail" rather than a fake "replay" sample
                _rest_do_detail(token, client, random.choice(all_ids), stats, failed_pool, own_fail_ids)
            else:
                eid, owner_client = popped
                _rest_do_replay(eid, tokens[owner_client], stats)
        else:
            if random.random() < 0.5:
                _rest_do_negative_no_token(stats)
            else:
                wrong_eid = random.choice(all_ids)
                owner = id_to_client[wrong_eid]
                other_clients = [c for c in CLIENTS if c != owner]
                _rest_do_negative_cross_client(wrong_eid, tokens[random.choice(other_clients)], stats)

        elapsed = time.time() - t0
        sleep_for = target_interval - elapsed
        if sleep_for > 0:
            stop_event.wait(sleep_for)


def start_rest_load(events, tokens, api_rps, api_clients):
    """Starts the REST load generator threads if api_rps > 0. Returns (stats, stop_event,
    threads) — pass to stop_rest_load() once the drain wait is over. Returns
    (RestLoadStats(), None, []) when disabled (api_rps <= 0), so callers can treat both cases
    uniformly."""
    stats = RestLoadStats()
    stop_event = threading.Event()
    if api_rps <= 0:
        return stats, None, []

    all_ids = [ev["event_id"] for ev in events]
    id_to_client = {ev["event_id"]: ev["client_id"] for ev in events}
    own_fail_ids = {ev["event_id"] for ev in events if ev["is_failing"]}
    failed_pool = FailedIdPool()
    target_interval = api_clients / api_rps

    threads = []
    for _ in range(api_clients):
        t = threading.Thread(
            target=rest_load_worker,
            args=(stop_event, tokens, all_ids, id_to_client, own_fail_ids, failed_pool, stats, target_interval),
            daemon=True,
        )
        t.start()
        threads.append(t)
    return stats, stop_event, threads


def stop_rest_load(stop_event, threads):
    if stop_event is None:
        return
    stop_event.set()
    for t in threads:
        t.join(timeout=5)


def rest_load_percentiles(latencies):
    if not latencies:
        return 0.0, 0.0
    ordered = sorted(latencies)
    n = len(ordered)
    p50 = ordered[int(0.50 * (n - 1))] * 1000
    p95 = ordered[int(0.95 * (n - 1))] * 1000
    return p50, p95


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


RANDOM_FAIL_RATIO_MIN = 0.001
RANDOM_FAIL_RATIO_MAX = 0.02


def resolve_fail_ratio(raw):
    """'random' draws a ratio in [0.1 %, 2 %], the range a real fleet of receivers shows on a bad day;
    a numeric value is validated and used as is."""
    if raw == "random":
        return round(random.uniform(RANDOM_FAIL_RATIO_MIN, RANDOM_FAIL_RATIO_MAX), 4)
    ratio = float(raw)
    if not 0.0 <= ratio <= 1.0:
        print("ERROR: --fail-ratio must be 'random' or a number between 0 and 1", file=sys.stderr)
        sys.exit(1)
    return ratio


def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--events", type=int, default=2000, help="number of events to publish (default 2000)")
    p.add_argument("--fail-ratio", type=str, default="random",
                   help="fraction of events made to fail every attempt; a number in [0, 1], or 'random' for a value "
                        "drawn uniformly in [0.001, 0.02] per run (default random)")
    p.add_argument("--concurrency", type=int, default=8, help="publishing threads, batches of 10 (default 8)")
    p.add_argument("--timeout", type=int, default=300, help="seconds to wait for the backlog to drain (default 300)")
    p.add_argument("--max-replays", type=int, default=200,
                   help="cap on replays launched by the REST load; each replay adds a 5-attempt cycle (default 200)")
    p.add_argument(
        "--poll-concurrency",
        type=int,
        default=DEFAULT_POLL_CONCURRENCY,
        help="threads for the single final id-by-id classification pass (default 8)",
    )
    p.add_argument("--run", default=uuid.uuid4().hex[:8], help="run tag, used to namespace event ids (default: random hex)")
    p.add_argument(
        "--wiremock-journal-limit",
        type=int,
        default=5000,
        help="WireMock --max-request-journal-entries (default 5000, matches compose.yaml); "
             "above this the receiver check switches from comparing against WireMock's request "
             "count to reading attempts.response_status from the API instead, since a bigger "
             "journal makes WireMock stall (it scans it on every request)",
    )
    p.add_argument(
        "--api-rps",
        type=float,
        default=20,
        help="approximate requests/s the REST load generator keeps against the self-service "
             "API while the drain wait runs (default 20; 0 disables it)",
    )
    p.add_argument(
        "--api-clients",
        type=int,
        default=4,
        help="threads used by the REST load generator (default 4)",
    )
    return p.parse_args()


def main():
    args = parse_args()
    global MAX_REPLAYS
    MAX_REPLAYS = args.max_replays
    if args.events <= 0:
        print("ERROR: --events must be > 0", file=sys.stderr)
        sys.exit(1)
    args.fail_ratio = resolve_fail_ratio(args.fail_ratio)

    run = args.run
    tokens = load_tokens()

    print(f"=== load_test run={run} events={args.events} fail_ratio={args.fail_ratio} "
          f"(induced failures: {round(args.events * args.fail_ratio)}) "
          f"concurrency={args.concurrency} timeout={args.timeout}s ===")

    events = build_events(args.events, args.fail_ratio, run)
    expected_fail_ids = {ev["event_id"] for ev in events if ev["is_failing"]}
    expected_ok_ids = {ev["event_id"] for ev in events if not ev["is_failing"]}

    print("Initial Prometheus snapshot...")
    prom_before = {
        "registered": prom_scalar_sum("sum(notifications_registered_total)"),
        "duplicates": prom_scalar_sum("sum(notifications_duplicates_total)"),
        "deliveries": prom_deliveries_snapshot(),
    }

    print("Resetting WireMock journal and registering failure stub...")
    wiremock_reset_journal()
    mapping_id = wiremock_register_failure_stub(run)

    exit_code = 1
    try:
        run_start_ts = int(time.time())

        print(f"Publishing {args.events} events ({args.concurrency} threads, batches of {BATCH_SIZE})...")
        publish_t0 = time.time()
        sent_total, publish_errors = publish_all(events, args.concurrency)
        publish_duration = time.time() - publish_t0
        throughput_publish = sent_total / publish_duration if publish_duration > 0 else 0.0
        print(f"  published {sent_total}/{args.events} in {publish_duration:.2f}s "
              f"({throughput_publish:.1f} msg/s)")
        if publish_errors:
            print(f"  WARN: {len(publish_errors)} errors during publishing (first 5): "
                  f"{publish_errors[:5]}")

        print(f"Waiting for the backlog to drain (timeout {args.timeout}s)...")
        drained, wait_duration, rest_stats = wait_for_drain(
            tokens, args.timeout, prom_before["deliveries"], events, args.api_rps, args.api_clients
        )
        if drained:
            print(f"  drain confirmed in {wait_duration:.0f}s ({DRAIN_CONFIRM_ROUNDS} consecutive empty rounds)")
        else:
            print(f"  WARN: drain not confirmed before the timeout ({args.timeout}s); "
                  f"running the final classification anyway")

        run_end_ts = int(time.time())
        total_duration = run_end_ts - run_start_ts

        print(f"Final classification (one pass per id, system at rest, "
              f"{args.poll_concurrency} threads)...")
        final_state, lost_ids, unresolved_ids = classify_all(events, tokens, args.poll_concurrency)
        if unresolved_ids:
            print(f"  WARN: {len(unresolved_ids)} ids still had no terminal status after "
                  f"{FINAL_CLASSIFY_RETRIES} extra passes")

        # ---- derive outcome sets
        registered_ids = set(final_state.keys())
        completed_ids = {eid for eid, (s, _, _) in final_state.items() if s == "completed"}
        failed_ids = {eid for eid, (s, _, _) in final_state.items() if s == "failed"}
        stuck_ids = registered_ids - completed_ids - failed_ids

        expected_completed_n = len(expected_ok_ids)
        expected_failed_n = len(expected_fail_ids)

        unexpected_failures = failed_ids - expected_fail_ids
        expected_but_not_failed = expected_fail_ids - failed_ids

        # Replays (launched by the REST load generator against already-failed -F- ids) add a
        # new delivery cycle of up to 5 more attempts on top of the original one, so a replayed
        # id's attempts_count is a multiple of 5 rather than exactly 5. What must always hold is
        # that the first cycle (cycle 0, the original delivery) took exactly 5 attempts, and
        # that the running total stays a multiple of 5.
        failed_with_wrong_attempts = set()
        for eid in failed_ids:
            _, attempts_count, attempts_list = final_state[eid]
            cycle0_count = sum(1 for a in attempts_list if a.get("cycle") == 0)
            if cycle0_count != 5 or attempts_count == 0 or attempts_count % 5 != 0:
                failed_with_wrong_attempts.add(eid)

        # Both attempt counts are always computed and printed, regardless of which receiver-check
        # variant ends up gating PASS below.
        all_attempts = [a for _, _, attempts_list in final_state.values() for a in attempts_list]
        attempts_with_status_n = sum(1 for a in all_attempts if a.get("response_status") is not None)
        attempts_null_status_n = sum(1 for a in all_attempts if a.get("response_status") is None)

        # -F- ids must have gotten a 503 from the stub on every attempt they made (cycle 0 and
        # any replay cycles); anything else means the attempt didn't actually reach the induced
        # failure stub.
        f_ids_not_all_503 = set()
        for eid in expected_fail_ids & registered_ids:
            attempts_list = final_state[eid][2]
            if any(a.get("response_status") != 503 for a in attempts_list):
                f_ids_not_all_503.add(eid)

        api_attempts_total = sum(a for _, a, _ in final_state.values())
        wiremock_count = wiremock_post_count(run)

        # ---- receiver-check variant: does the run's expected POST volume fit WireMock's journal?
        expected_posts_base = expected_completed_n + 5 * expected_failed_n
        expected_posts_final = expected_posts_base + 5 * rest_stats.replays_launched
        journal_variant = "A" if expected_posts_final <= args.wiremock_journal_limit else "B"

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
        print("REPORT")
        print("=" * 78)
        print(f"Published:             {sent_total}/{args.events} in {publish_duration:.2f}s "
              f"({throughput_publish:.1f} msg/s)")
        print(f"Registered (API):      {len(registered_ids)}/{args.events}")
        print(f"Lost (404 at the end): {len(lost_ids)}")
        if lost_ids:
            if len(lost_ids) <= 20:
                print(f"  lost ids: {lost_ids}")
            else:
                print(f"  (more than 20, not listed; first 20: {lost_ids[:20]})")
        print(f"Completed:             expected {expected_completed_n}, got {len(completed_ids)}")
        print(f"Failed:                expected {expected_failed_n}, got {len(failed_ids)}")
        if unexpected_failures:
            sample = list(unexpected_failures)[:10]
            print(f"  WARN: {len(unexpected_failures)} ids failed without being expected to (e.g.: {sample})")
        if expected_but_not_failed:
            sample = list(expected_but_not_failed)[:10]
            print(f"  WARN: {len(expected_but_not_failed)} -F- ids did not end up failed (e.g.: {sample})")
        if failed_with_wrong_attempts:
            sample = list(failed_with_wrong_attempts)[:10]
            print(f"  WARN: {len(failed_with_wrong_attempts)} failed without 5 attempts in cycle 0 or with "
                  f"attempts_count not a multiple of 5 (e.g.: {sample})")
        if f_ids_not_all_503:
            sample = list(f_ids_not_all_503)[:10]
            print(f"  WARN: {len(f_ids_not_all_503)} -F- ids with some attempt other than 503 (e.g.: {sample})")
        if stuck_ids:
            sample = list(stuck_ids)[:10]
            print(f"  WARN: {len(stuck_ids)} ids registered but without terminal status after the final classification (e.g.: {sample})")
        print(f"Total duration:        {total_duration}s, delivery throughput: {throughput_deliveries:.2f}/s "
              f"(completed+failed={delivered_n})")
        print(f"API attempts (sum attempts_count): {api_attempts_total}")
        print(f"POST /webhook on WireMock (filtered by run={run}): {wiremock_count}")
        if wiremock_count is None:
            print("  WARN: could not read /__admin/requests/count from WireMock")
        print(f"Attempts with numeric response_status (a): {attempts_with_status_n}")
        print(f"Attempts with null response_status / I/O, timeout or connection error (b): "
              f"{attempts_null_status_n}")
        print(f"Replays launched by the REST load: {rest_stats.replays_launched}")
        print(f"expected_posts (completed + 5*failed + 5*replays) = "
              f"{expected_completed_n} + 5*{expected_failed_n} + 5*{rest_stats.replays_launched} = "
              f"{expected_posts_final}, WireMock journal limit = {args.wiremock_journal_limit}")
        if journal_variant == "A":
            print(f"Receiver-check variant: A (expected_posts {expected_posts_final} <= "
                  f"limit {args.wiremock_journal_limit}: WireMock's journal covers the run, "
                  f"API attempts are compared against WireMock's POST count)")
        else:
            print(f"Receiver-check variant: B (expected_posts {expected_posts_final} > "
                  f"limit {args.wiremock_journal_limit}: WireMock's journal does NOT cover the run "
                  f"and its count is not reliable, attempts[].response_status from the final "
                  f"classification pass is used instead: (b) must be 0 and -F- ids must have all "
                  f"their attempts at 503)")
        print(f"Prometheus delta registered: {registered_delta:+.0f}")
        print(f"Prometheus delta duplicates: {duplicates_delta:+.0f}")
        print("Prometheus delta deliveries by status:")
        for status in sorted(deliveries_delta):
            print(f"  {status}: {deliveries_delta[status]:+.0f}")
        print(f"p95 webhook latency (run window): "
              f"{'%.3fs' % p95_latency if p95_latency is not None else 'no data'}")
        print(f"max(notifications_attempts_due) (run window): "
              f"{max_attempts_due if max_attempts_due is not None else 'no data'}")

        print()
        print("REST API under load:")
        if args.api_rps <= 0:
            print("  (disabled, --api-rps 0)")
        else:
            print(f"  {'type':22s} {'n':>6s} {'p50 ms':>8s} {'p95 ms':>8s} {'unexpected':>12s}")
            for bucket in REST_BUCKETS:
                lat = rest_stats.latencies[bucket]
                n = len(lat)
                p50, p95 = rest_load_percentiles(lat)
                unexpected_n = rest_stats.unexpected_count[bucket]
                print(f"  {bucket:22s} {n:6d} {p50:8.1f} {p95:8.1f} {unexpected_n:12d}")
                if rest_stats.unexpected_samples[bucket]:
                    print(f"    unexpected codes (sample): {rest_stats.unexpected_samples[bucket]}")
            print(f"  total requests: {rest_stats.total_requests()}, "
                  f"5xx: {rest_stats.five_xx_count}, unexpected: {rest_stats.total_unexpected()}, "
                  f"replays launched: {rest_stats.replays_launched}")

        # ---- PASS/FAIL
        if journal_variant == "A":
            receiver_check_name = "API attempts == WireMock POST count (journal covers the run)"
            receiver_check_ok = wiremock_count is not None and api_attempts_total == wiremock_count
        else:
            receiver_check_name = (
                "reception confirmed via the API (WireMock journal does not cover the run): "
                "0 attempts with null response_status and -F- all at 503"
            )
            receiver_check_ok = attempts_null_status_n == 0 and not f_ids_not_all_503

        checks = {
            "0 lost": len(lost_ids) == 0,
            "completed == expected": len(completed_ids) == expected_completed_n,
            "failed == expected (exact ids, cycle 0 with 5 attempts, attempts_count multiple of 5)": (
                len(failed_ids) == expected_failed_n
                and not unexpected_failures
                and not expected_but_not_failed
                and not failed_with_wrong_attempts
            ),
            receiver_check_name: receiver_check_ok,
            "duplicates delta == 0": duplicates_delta == 0,
        }
        if args.api_rps > 0:
            checks["REST API under load: 0 5xx and 0 unexpected codes"] = (
                rest_stats.five_xx_count == 0 and rest_stats.total_unexpected() == 0
            )
        print()
        print("Criteria:")
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
        print("Deleting WireMock failure stub...")
        wiremock_delete_mapping(mapping_id)

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
