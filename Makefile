SHELL := /bin/bash
COMPOSE := docker compose

.PHONY: help preflight keys infra up up-all down logs ps build test token emit replay clean

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

preflight: ## Check Docker, free ports and .env before starting
	@scripts/preflight.sh

keys: ## Generate the RS256 key pair used to sign and verify demo JWTs
	@mkdir -p docker/keys
	@test -f docker/keys/jwt-private.pem || openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out docker/keys/jwt-private.pem 2>/dev/null
	@openssl rsa -in docker/keys/jwt-private.pem -pubout -out docker/keys/jwt-public.pem 2>/dev/null
	@echo "keys in docker/keys/"

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

build: ## Build the application without running tests
	./mvnw -q -DskipTests package

test: ## Run the full test suite
	./mvnw verify

token: ## Issue a demo JWT: make token CLIENT=CLIENT001
	@scripts/token.sh $(CLIENT)

emit: ## Emit one event through the simulator: make emit CLIENT=CLIENT001 TYPE=credit_deposit
	@curl -sS -X POST "http://localhost:$${SIMULATOR_PORT:-8090}/simulator/events" \
	  -H 'Content-Type: application/json' \
	  -d '{"client_id":"$(CLIENT)","event_type":"$(TYPE)","content":"Manual event from Makefile"}' && echo

replay: ## Replay a failed notification: make replay ID=EVT003 CLIENT=CLIENT002
	@curl -sS -X POST "http://localhost:$${API_PORT:-8080}/notification_events/$(ID)/replay" \
	  -H "Authorization: Bearer $$(scripts/token.sh $(CLIENT))" -i

clean: ## Remove build output
	./mvnw -q clean
