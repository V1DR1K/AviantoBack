#!/usr/bin/env bash
set -euo pipefail

ROOT=/home/Avianto/AviantoBack
set -a
source "$ROOT/.env"
set +a
BACKUPS="$ROOT/backups"
umask 077
mkdir -p "$BACKUPS"
chown deploy:deploy "$BACKUPS"
chmod 750 "$BACKUPS"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
temporary="$BACKUPS/.avianto-${stamp}.sql.gz.tmp"
final="$BACKUPS/avianto-${stamp}.sql.gz"
trap 'rm -f "$temporary"' EXIT
docker compose --env-file "$ROOT/.env" -f "$ROOT/docker-compose.yml" exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" | gzip > "$temporary"
mv -f "$temporary" "$final"
chmod 600 "$final"
find "$BACKUPS" -type f -name 'avianto-*.sql.gz' -mtime +14 -delete
