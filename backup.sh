#!/usr/bin/env bash
set -euo pipefail

ROOT=/home/Avianto/AviantoBack
BACKUPS="$ROOT/backups"
mkdir -p "$BACKUPS"
chown deploy:deploy "$BACKUPS"
chmod 750 "$BACKUPS"

docker compose --env-file "$ROOT/.env" -f "$ROOT/docker-compose.yml" exec -T postgres pg_dump -U avianto -d avianto | gzip > "$BACKUPS/avianto-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
find "$BACKUPS" -type f -name 'avianto-*.sql.gz' -mtime +14 -delete
