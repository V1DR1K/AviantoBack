#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  printf 'Uso: %s /ruta/al/respaldo.sql.gz\n' "$0" >&2
  exit 64
fi

ROOT=/home/Avianto/AviantoBack
gunzip -c "$1" | docker compose --env-file "$ROOT/.env" -f "$ROOT/docker-compose.yml" exec -T postgres psql -U avianto -d avianto
