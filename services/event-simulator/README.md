# event-simulator

Stands in for the Cobre platform's event bus in the local stack. On startup it replays the ten
events of the reference dataset with their original identifiers and timestamps, then keeps
publishing derived events — new identifiers, random combinations of client, event type and content
drawn from the same dataset — onto the `account-events` queue at a configurable interval. It is a
standalone Spring Boot project (`co.cobre.simulator`), deliberately independent of `domain` and
`application` in [`services/notifications`](../notifications): in production the platform's own
event bus publishes to the same queue and this module is not deployed at all. See the root
[`README.md`](../../README.md#how-it-works) for where it sits in the overall flow.

## Message contract

Publishes one JSON message per account event to SQS (`SqsTemplate.send`, no batching, no message
attributes): `event_id`, `event_type`, `client_id`, `content`, `occurred_at`. Full schema, delivery
guarantees (at-least-once, no ordering, visibility timeout, dead-letter queue) and examples:
[`../../docs/api/asyncapi.yaml`](../../docs/api/asyncapi.yaml).

## Emitting an event manually

`POST /simulator/events` generates one event from the request and publishes it the same way as the
interval-based emission:

```bash
curl -sS -X POST "http://localhost:8090/simulator/events" \
  -H 'Content-Type: application/json' \
  -d '{"client_id":"CLIENT001","event_type":"credit_deposit","content":"Manual event"}'
# 202 Accepted, body: {"event_id":"<generated>"}
```

Equivalent with the root `Makefile`: `make emit CLIENT=CLIENT001 TYPE=credit_deposit`.

## Configuration

`SimulatorProperties` (prefix `simulator`, in
[`src/main/resources/application.yaml`](src/main/resources/application.yaml)):

| Environment variable | Default | What it controls |
|---|---|---|
| `SIMULATOR_EMIT_INTERVAL` | `2s` | Interval between derived-event emissions |
| `SIMULATOR_EMIT_REFERENCE_ON_START` | `true` | Whether the ten reference events are replayed once at startup |
| `SIMULATOR_EVENTS_FILE` | `classpath:notification_events.json` | Source dataset; the Compose stack mounts `docs/notification_events.json` here instead, so both services read the same file |
| `SQS_ENDPOINT` / `SQS_QUEUE_NAME` / `SQS_REGION` | ElasticMQ locally | Queue the events are published to |
| `SERVER_PORT` | `8080` (published as `${SIMULATOR_PORT:-8090}` by Compose) | HTTP port |

## Building and testing

```bash
./mvnw verify   # unit tests, a Testcontainers-based SQS publisher IT, and PMD/JaCoCo (100% line coverage)
```
