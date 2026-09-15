#!/usr/bin/env bash
# Issues a demo JWT (RS256) for a client id, signed with docker/keys/jwt-private.pem.
# Usage: scripts/token.sh CLIENT001   -> prints the token
# Mirrors the shape of Cobre's real token: Bearer, 20-minute lifetime. The claim carrying the
# client id and the audience are configurable in the API (JWT_CLIENT_CLAIM, JWT_AUDIENCE).
set -euo pipefail

cd "$(dirname "$0")/.."
client="${1:-}"
[ -n "$client" ] || { echo "usage: $0 CLIENT_ID" >&2; exit 1; }
[ -f docker/keys/jwt-private.pem ] || { echo "run 'make keys' first" >&2; exit 1; }

if [ -f .env ]; then set -a; source .env; set +a; fi
aud="${JWT_AUDIENCE:-account-event-notification}"
now=$(date +%s)
exp=$((now + 1200))

b64url() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }

header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
payload=$(printf '{"iss":"account-event-notification-local","sub":"%s","aud":"%s","iat":%d,"exp":%d}' "$client" "$aud" "$now" "$exp" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -sign docker/keys/jwt-private.pem | b64url)

printf '%s.%s.%s\n' "$header" "$payload" "$signature"
