#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROJECT="avianto-migration-$$"
COMPOSE=(docker compose --env-file "$ROOT/.env" -p "$PROJECT" -f "$ROOT/docker-compose.full.yml")

set -a
source "$ROOT/.env"
set +a

cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" up -d --build postgres app

for attempt in $(seq 1 60); do
  if "${COMPOSE[@]}" exec -T app wget -qO- http://localhost:8081/actuator/health/readiness >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    "${COMPOSE[@]}" logs app
    exit 1
  fi
  sleep 2
done

history=$(${COMPOSE[*]} exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc \
  "select (select count(*) from flyway_schema_history) || ' migrations; latest=' || version from flyway_schema_history order by installed_rank desc limit 1")
printf 'Fresh migration validation: %s\n' "$history"
