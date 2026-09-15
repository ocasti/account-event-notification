#!/usr/bin/env bash
# Verifies the local machine is ready to run the stack. Exits non-zero on the first blocking problem.
set -euo pipefail

cd "$(dirname "$0")/.."

ok()   { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
warn() { printf '  \033[33mWARN\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; exit 1; }

echo "Preflight"

command -v docker >/dev/null || fail "docker not installed"
docker info >/dev/null 2>&1 || fail "docker daemon not running"
ok "docker $(docker version --format '{{.Server.Version}}')"

docker compose version >/dev/null 2>&1 || fail "docker compose plugin missing"
ok "docker compose $(docker compose version --short)"

mem_bytes=$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)
mem_gb=$(( mem_bytes / 1024 / 1024 / 1024 ))
if [ "$mem_gb" -lt 4 ]; then warn "docker has ${mem_gb} GB; 4 GB recommended"; else ok "docker memory ${mem_gb} GB"; fi

if [ ! -f .env ]; then
  cp .env.example .env
  warn ".env created from .env.example; edit WEBHOOK_URL to point at a real receiver"
else
  ok ".env present"
fi
set -a; source .env; set +a

for spec in "API_PORT:${API_PORT:-8080}" "SIMULATOR_PORT:${SIMULATOR_PORT:-8090}" "WIREMOCK_PORT:${WIREMOCK_PORT:-8089}" \
            "POSTGRES_PORT:${POSTGRES_PORT:-5432}" "ELASTICMQ_UI_PORT:${ELASTICMQ_UI_PORT:-9325}" \
            "PROMETHEUS_PORT:${PROMETHEUS_PORT:-9090}" "GRAFANA_PORT:${GRAFANA_PORT:-3000}"; do
  name=${spec%%:*}; port=${spec##*:}
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "port $port ($name) is in use; change it in .env"
  fi
done
ok "ports free"

if [ ! -f docker/keys/jwt-public.pem ]; then
  warn "JWT keys missing; run 'make keys'"
else
  ok "JWT keys present"
fi

echo "Ready."
