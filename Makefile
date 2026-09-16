SHELL := /bin/bash
COMPOSE := docker compose -f deploy/local/compose.yaml --env-file .env

EVENTS ?= 2000
FAIL_RATIO ?= 0.10
CONCURRENCY ?= 8
TIMEOUT ?= 300
WIREMOCK_JOURNAL_LIMIT ?= 5000
API_RPS ?= 20
API_CLIENTS ?= 4

.PHONY: help preflight keys infra up up-all down logs ps build test token emit replay clean load openapi

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

preflight: ## Check Docker, free ports and .env before starting
	@scripts/preflight.sh

keys: ## Generate the RS256 key pair used to sign and verify demo JWTs
	@mkdir -p deploy/local/keys
	@test -f deploy/local/keys/jwt-private.pem || openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out deploy/local/keys/jwt-private.pem 2>/dev/null
	@openssl rsa -in deploy/local/keys/jwt-private.pem -pubout -out deploy/local/keys/jwt-public.pem 2>/dev/null
	@echo "keys in deploy/local/keys/"

infra: ## Start only infrastructure (postgres, elasticmq, wiremock)
	$(COMPOSE) --profile infra up -d --wait

up: ## Start the core stack (infra + api + worker + simulator)
	$(COMPOSE) --profile infra --profile app up -d --build --wait

up-all: ## Start everything including observability (prometheus, grafana)
	$(COMPOSE) --profile infra --profile app --profile observability up -d --build --wait

down: ## Stop and remove everything
	$(COMPOSE) --profile infra --profile app --profile observability down -v

logs: ## Follow logs of api, worker and simulator
	$(COMPOSE) logs -f notifications-api notifications-worker event-simulator

ps: ## Show container status
	$(COMPOSE) ps

build: ## Build both services without running tests
	cd services/notifications && ./mvnw -q -DskipTests package
	cd services/event-simulator && ./mvnw -q -DskipTests package

test: ## Run the full test suite of both services
	cd services/notifications && ./mvnw verify
	cd services/event-simulator && ./mvnw verify

token: ## Issue a demo JWT: make token CLIENT=CLIENT001
	@scripts/token.sh $(CLIENT)

emit: ## Emit one event through the simulator: make emit CLIENT=CLIENT001 TYPE=credit_deposit
	@curl -sS -X POST "http://localhost:$${SIMULATOR_PORT:-8090}/simulator/events" \
	  -H 'Content-Type: application/json' \
	  -d '{"client_id":"$(CLIENT)","event_type":"$(TYPE)","content":"Manual event from Makefile"}' && echo

replay: ## Replay a failed notification: make replay ID=EVT003 CLIENT=CLIENT002
	@curl -sS -X POST "http://localhost:$${API_PORT:-8080}/notification_events/$(ID)/replay" \
	  -H "Authorization: Bearer $$(scripts/token.sh $(CLIENT))" -i

clean: ## Remove build output of both services
	cd services/notifications && ./mvnw -q clean
	cd services/event-simulator && ./mvnw -q clean

openapi: ## Export the OpenAPI spec from the running API to docs/api/openapi.json
	@curl -sS "http://localhost:$${API_PORT:-8080}/v3/api-docs" \
	  -H "Authorization: Bearer $$(scripts/token.sh CLIENT001)" \
	  | python3 -m json.tool > docs/api/openapi.json

load: ## Load test against the running stack: make load EVENTS=2000 FAIL_RATIO=0.10 CONCURRENCY=8 TIMEOUT=300 WIREMOCK_JOURNAL_LIMIT=5000 API_RPS=20 API_CLIENTS=4
	@TOKEN_CLIENT001=$$(scripts/token.sh CLIENT001) && \
	TOKEN_CLIENT002=$$(scripts/token.sh CLIENT002) && \
	TOKEN_CLIENT003=$$(scripts/token.sh CLIENT003) && \
	docker run --rm --network account-event-notification \
	  -v "$$(pwd)/scripts:/scripts:ro" \
	  -e TOKEN_CLIENT001="$$TOKEN_CLIENT001" \
	  -e TOKEN_CLIENT002="$$TOKEN_CLIENT002" \
	  -e TOKEN_CLIENT003="$$TOKEN_CLIENT003" \
	  python:3.12-alpine python /scripts/load_test.py \
	    --events $(EVENTS) --fail-ratio $(FAIL_RATIO) --concurrency $(CONCURRENCY) --timeout $(TIMEOUT) \
	    --wiremock-journal-limit $(WIREMOCK_JOURNAL_LIMIT) --api-rps $(API_RPS) --api-clients $(API_CLIENTS)
