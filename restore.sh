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
gunzip -c "$1" | docker compose --env-file "$ROOT/.env" -f "$ROOT/docker-compose.yml" exec -T postgres psql --set=ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"
