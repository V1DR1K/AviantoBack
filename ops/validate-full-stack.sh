#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROJECT="avianto-full-verify-$$"
export AVIANTO_PROXY_PORT=18080
COMPOSE=(docker compose --env-file "$ROOT/.env" -p "$PROJECT" -f "$ROOT/docker-compose.full.yml")

cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" up -d --build

for attempt in $(seq 1 90); do
  if "${COMPOSE[@]}" exec -T app wget -qO- http://localhost:8081/actuator/health/readiness >/dev/null 2>&1 \
    && "${COMPOSE[@]}" exec -T frontend node -e "fetch('http://127.0.0.1:3000/').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    "${COMPOSE[@]}" ps
    exit 1
  fi
  sleep 2
done

curl --fail --silent --show-error "http://127.0.0.1:${AVIANTO_PROXY_PORT}/" >/dev/null
printf 'Full stack validation: OK\n'
