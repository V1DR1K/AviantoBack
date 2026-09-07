#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  printf 'Uso: %s /ruta/al/respaldo.sql.gz\n' "$0" >&2
  exit 64
fi

backup=$1
container="avianto-restore-verify-$$"
password="restore-verification-only"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run --detach --name "$container" \
  --env POSTGRES_DB=avianto_restore \
  --env POSTGRES_USER=avianto \
  --env POSTGRES_PASSWORD="$password" \
  postgres@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73 >/dev/null

for attempt in $(seq 1 30); do
  if docker exec "$container" pg_isready -U avianto -d avianto_restore >/dev/null 2>&1; then break; fi
  if [[ "$attempt" == "30" ]]; then exit 1; fi
  sleep 1
done

gunzip -c "$backup" | docker exec -i "$container" psql --set=ON_ERROR_STOP=1 --single-transaction -U avianto -d avianto_restore >/dev/null
docker exec "$container" psql -U avianto -d avianto_restore -Atqc \
  "select 'ficha=' || count(*) from ficha; select 'ficha_foto=' || count(*) from ficha_foto; select 'pago=' || count(*) from pago;"
printf 'Restore verification: OK\n'
