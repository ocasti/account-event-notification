# account-event-notification

Webhook delivery of account events with configurable retries, and a self-service API for clients to query and replay their own notifications. Hexagonal architecture, Java 21, Spring Boot, running locally on Docker Compose with a simulated event source and a production design targeting AWS.

Design document: `docs/01-system-design.html` (RFC, open in a browser). Security analysis: `docs/02-security.md`. AI usage log: `docs/03-ai-usage.md`. Local stack details: `deploy/local/README.md`.

## Requirements

- Docker and the Docker Compose plugin. Nothing else is required to run the stack: both services build inside Docker, so no local Java or Maven installation is needed.
- Java 21 only if you plan to develop against the code outside a container (run a module with `./mvnw`, use an IDE, run `make test`).

## Quick start

```bash
make preflight   # checks Docker, free ports and memory; creates .env from .env.example
make keys        # generates the RS256 key pair used to sign demo JWTs into deploy/local/keys/
make up          # builds and starts postgres, elasticmq, wiremock, api, worker, simulator
```

## What `make up` starts

| Container | Image | Published port | What it is for |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `${POSTGRES_PORT:-5432}` → 5432 | Single source of truth: `notification_events`, `delivery_attempts`, `subscriptions` |
| `elasticmq` | `softwaremill/elasticmq-native:1.6.12` | `${ELASTICMQ_UI_PORT:-9325}` → 9325 | Queue with the SQS API (consumed internally on 9324); 9325 serves the statistics UI used by the healthcheck |
| `wiremock` | `wiremock/wiremock:3.13.1` | `${WIREMOCK_PORT:-8089}` → 8080 | Webhook receiver for the demo: 200 by default, 503 for `EVT003`/`EVT005`/`EVT009`, and a slow mapping to exercise the read timeout |
| `notifications-api` | `cobre/notifications:local`, profile `api` | `${API_PORT:-8080}` → 8080 | Self-service REST API (list, detail, replay), JWT-protected; runs the Flyway migrations |
| `notifications-worker` | `cobre/notifications:local`, profile `worker` | none (scales with `--scale notifications-worker=N`) | Consumes the queue, claims due delivery attempts and delivers webhooks with retries |
| `event-simulator` | `cobre/event-simulator:local` | `${SIMULATOR_PORT:-8090}` → 8080 | Stands in for the platform that emits events: replays the reference data set and keeps generating derived events |
| `postgres-exporter` (only with `make up-all`) | `prometheuscommunity/postgres-exporter:v0.20.1` | none (scraped inside the network) | Exposes Postgres metrics for Prometheus |
| `node-exporter` (only with `make up-all`) | `quay.io/prometheus/node-exporter:v1.8.2` | none (scraped inside the network) | Exposes host/VM-level metrics (CPU, memory, disk, network) for Prometheus; see `deploy/local/README.md` for why not per-container metrics under OrbStack |
| `prometheus` (only with `make up-all`) | `prom/prometheus:latest` | `${PROMETHEUS_PORT:-9090}` → 9090 | Scrapes `/actuator/prometheus` from the api and every worker replica, plus `postgres-exporter` and `node-exporter` |
| `grafana` (only with `make up-all`) | `grafana/grafana:latest` | `${GRAFANA_PORT:-3001}` → 3000 | Provisioned dashboard: delivery rate by status, webhook p95 by client, attempts due |

`make up` starts the first 6 rows (infra + app); `make up-all` starts all 10.

## Demo flow

Issue a JWT for a client (20-minute lifetime, RS256, `sub` claim carries the client id):

```bash
make token CLIENT=CLIENT002
```

List that client's notifications, filtering by `delivery_status`, a date range and paging by cursor:

```bash
TOKEN=$(make token CLIENT=CLIENT002)
curl -sS "http://localhost:8080/notification_events?delivery_status=failed&from=2024-03-15T00:00:00Z&to=2024-03-16T00:00:00Z&limit=20" \
  -H "Authorization: Bearer $TOKEN"
# paginate with the cursor returned as next_cursor:
curl -sS "http://localhost:8080/notification_events?limit=20&cursor=<next_cursor>" \
  -H "Authorization: Bearer $TOKEN"
```

Get the detail of one event, including its delivery attempts:

```bash
curl -sS "http://localhost:8080/notification_events/EVT005" -H "Authorization: Bearer $TOKEN"
```

Replay a failed notification (opens a new delivery cycle, picked up by the worker on its next tick):

```bash
make replay ID=EVT005 CLIENT=CLIENT002   # 202 Accepted if delivery_status was failed
make replay ID=EVT005 CLIENT=CLIENT002   # 409 Conflict on the second call: no longer failed
make replay ID=EVT999 CLIENT=CLIENT002   # 404 Not Found: unknown id, or belongs to another client
```

Emit one event manually through the simulator instead of waiting for the continuous emission:

```bash
make emit CLIENT=CLIENT001 TYPE=credit_deposit
```

Count how many POSTs the worker sent to the WireMock receiver, to check delivery volume (for example after scaling workers):

```bash
curl -sS -X POST "http://localhost:8089/__admin/requests/count" \
  -H 'Content-Type: application/json' \
  -d '{"method":"POST","urlPath":"/webhook"}'
```

Add Prometheus and Grafana to the stack:

```bash
make up-all
# Grafana on http://localhost:${GRAFANA_PORT:-3001} (anonymous access, provisioned dashboard)
```

Run a load test against the stack (publishes events straight onto the queue, drains the backlog,
and checks nothing was lost or double-delivered):

```bash
make load   # EVENTS=2000 FAIL_RATIO=0.10 CONCURRENCY=8 TIMEOUT=300 API_RPS=20 API_CLIENTS=4 by default
```

## API documentation

Three contracts, kept next to the code in [`docs/api/`](docs/api/):

- [`docs/api/openapi.json`](docs/api/openapi.json) — the self-service REST API (list, detail,
  replay), generated from the running code with `make openapi`.
- [`docs/api/asyncapi.yaml`](docs/api/asyncapi.yaml) — the `account-events` queue: the message
  the worker consumes and its dead-letter queue.
- [`docs/api/webhook-contract.md`](docs/api/webhook-contract.md) — the outbound webhook the
  worker sends to each client's receiver: headers, HMAC signature, retries, replay.

[`docs/api/README.md`](docs/api/README.md) explains how to view each one. To browse the REST API
against the running stack with Swagger UI (profile `local` only):

```bash
make up
make token CLIENT=CLIENT002   # copy the token, then paste it into the "Authorize" dialog
# open http://localhost:8080/swagger-ui/index.html
```

## Pointing at a real receiver

Edit `WEBHOOK_URL` in `.env`, then:

```bash
make down && make up
```

A restart is required, not just a config reload: the seeded subscriptions' URL is baked into the Flyway migration `V2__initial_subscriptions.sql` as a placeholder resolved at migration time. `make down` removes the Postgres container (no persistent volume), so `make up` re-runs the migration with the new `WEBHOOK_URL` and the subscriptions are recreated pointing at the new receiver.

## Running the tests

```bash
make test   # ./mvnw verify in services/notifications and services/event-simulator
```

`verify` also runs PMD (`pmd-ruleset.xml` in each service): unused imports, unused private members, empty or generic catch blocks and lost stack traces fail the build, locally and in CI.

Both modules use Testcontainers for integration tests, which needs a working Docker socket. On macOS with OrbStack (not Docker Desktop), Testcontainers may not auto-detect the socket; if `make test` fails to reach Docker, run:

```bash
DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock TESTCONTAINERS_RYUK_DISABLED=true make test
```

This is only needed on macOS with OrbStack; it is not required by `make up`, which uses Compose directly.

The delivery engine schedules and claims attempts with the database clock, so the Docker VM clock must match the host. After a laptop sleep, OrbStack or Docker Desktop can drift by hours; the symptom is a worker that claims nothing and time-based integration tests that hang or fail. `make preflight` checks the skew; if it reports one, restart the VM (`orbctl stop && orbctl start` on OrbStack).

## Repository structure

```
services/
  notifications/            Maven reactor of three modules, produces cobre/notifications
    domain/                 co.cobre.notifications.domain: entities, state machine, retry policy, value objects (pure Java, no Spring)
    application/             co.cobre.notifications.application.port: output ports; .usecase: the five use cases (pure Java)
    infrastructure/          co.cobre.notifications.infrastructure.{config,persistence,rest,security,webhook,worker}: Spring Boot adapters
  event-simulator/          co.cobre.simulator: standalone Maven project, stands in for the event-emitting platform (local only)
deploy/
  local/                    compose.yaml plus ElasticMQ, WireMock, Prometheus, Grafana config and the generated JWT keys
docker/                     notifications.Dockerfile and simulator.Dockerfile (build context: repository root)
docs/                       RFC (01-system-design.html), security analysis (02-security.md), AI usage log (03-ai-usage.md), reference data set, api/ (OpenAPI, AsyncAPI, webhook contract)
scripts/                    preflight.sh and token.sh
```

Each service builds on its own: `cd services/notifications && ./mvnw verify` (or `services/event-simulator`). The `Makefile` runs both and drives Compose with `-f deploy/local/compose.yaml --env-file .env`.

## Further reading

- [`docs/01-system-design.html`](docs/01-system-design.html): the RFC (architecture, sequences, data model, decisions).
- [`docs/02-security.md`](docs/02-security.md): OWASP API Security Top 10 analysis against the code.
- [`docs/03-ai-usage.md`](docs/03-ai-usage.md): AI usage log.
- [`deploy/local/README.md`](deploy/local/README.md): local stack reference (profiles, ports, WireMock scenarios, Grafana panels).
- [`docs/api/`](docs/api/): OpenAPI (REST), AsyncAPI (`account-events` queue) and the outbound webhook contract.
