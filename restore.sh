#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  printf 'Uso: %s /ruta/al/respaldo.sql.gz\n' "$0" >&2
  exit 64
fi

ROOT=/home/Avianto/AviantoBack
set -a
source "$ROOT/.env"
set +a
if [[ "${AVIANTO_ALLOW_RESTORE:-}" != "1" ]]; then
  printf 'Restauración bloqueada. Confirmá explícitamente con AVIANTO_ALLOW_RESTORE=1.\n' >&2
  exit 77
fi
running_services=$(docker compose --env-file "$ROOT/.env" -f "$ROOT/docker-compose.yml" ps --services --status running)
for service in $running_services; do
  if [[ "$service" == "app" ]]; then
    printf 'Restauración bloqueada: detené el servicio app antes de continuar.\n' >&2
    exit 78
  fi
done
gunzip -c "$1" | docker compose --env-file "$ROOT/.env" -f "$ROOT/docker-compose.yml" exec -T postgres psql --set=ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"
