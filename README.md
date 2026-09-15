# account-event-notification

Webhook delivery of account events with retries, and a self-service API for clients to query and replay their notifications. Hexagonal architecture, Java 21, Spring Boot.

Design document: `docs/01-system-design.html` (RFC, open in a browser). Security analysis: `docs/02-security.md`. AI usage log: `docs/03-ai-usage.md`.

## Quick start

```bash
make preflight   # checks Docker, ports, creates .env from .env.example
make keys        # RS256 key pair for demo JWTs
make up          # postgres, elasticmq, wiremock, api, worker, simulator
make logs
```

Point the seeded subscriptions at a real receiver by editing `WEBHOOK_URL` in `.env` and running `make up` again.

## Everyday commands

| Command | What it does |
|---|---|
| `make token CLIENT=CLIENT002` | Issues a 20-minute JWT for that client |
| `make emit CLIENT=CLIENT001 TYPE=credit_deposit` | Emits one event through the simulator |
| `make replay ID=EVT003 CLIENT=CLIENT002` | Replays a failed notification |
| `make up-all` | Adds Prometheus and Grafana |
| `make test` | Full test suite (needs Docker for Testcontainers) |
| `make down` | Stops everything and removes volumes |

## Layout

```
domain/            pure Java: entities, state machine, retry policy
application/       use cases and ports
infrastructure/    Spring Boot adapters: REST, JPA, SQS, HTTPS, scheduler, security, metrics
event-simulator/   stands in for the event-producing platform (local only)
docker/            Dockerfiles and container configuration
docs/              RFC, security analysis, AI usage log, reference data
scripts/           preflight and token helpers
```
