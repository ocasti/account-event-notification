# API documentation

Three contracts, one per interface this system exposes or consumes:

| File | What it is | Covers |
|---|---|---|
| [`openapi.json`](openapi.json) | OpenAPI 3 spec of the self-service REST API | `GET /notification_events`, `GET /notification_events/{id}`, `POST /notification_events/{id}/replay` — list, detail and replay, JWT-protected |
| [`asyncapi.yaml`](asyncapi.yaml) | AsyncAPI 3 spec of the `account-events` queue | The message `notifications-worker` consumes and the platform (or `event-simulator` locally) publishes, plus the `account-events-dlq` dead-letter queue |
| [`webhook-contract.md`](webhook-contract.md) | Contract of the outbound webhook | The `POST` `notifications-worker` sends to each client's receiver: headers, body, HMAC signature, response handling, retries and replay |

`openapi.json` is generated from the running code with `make openapi` (see the root `README.md`);
`asyncapi.yaml` and `webhook-contract.md` are written by hand against the same source
(`services/notifications/`, `deploy/local/elasticmq/custom.conf`) and should be updated alongside
it when the contract changes.

## Viewing them

- **`openapi.json`** — either run the stack (`make up`) and open Swagger UI at
  `http://localhost:8080/swagger-ui/index.html` (profile `local` only, requires a Bearer token
  from `make token`), or paste the file into the online
  [Swagger Editor](https://editor.swagger.io/).
- **`asyncapi.yaml`** — paste it into the online
  [AsyncAPI Studio](https://studio.asyncapi.com/), or render it locally without installing
  anything in this repo: `npx @asyncapi/cli render docs/api/asyncapi.yaml` (or
  `npx @asyncapi/cli validate docs/api/asyncapi.yaml` to just validate it).
- **`webhook-contract.md`** — plain Markdown, read it directly.
