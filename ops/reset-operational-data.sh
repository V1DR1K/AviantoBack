#!/bin/sh
set -eu

if [ "${RESET_OPERATIONAL_DATA:-}" != "YES" ]; then
  printf '%s\n' 'Refusing to reset data. Set RESET_OPERATIONAL_DATA=YES explicitly.' >&2
  exit 2
fi

DB_URL="${PSQL_DATABASE_URL:-${DATABASE_URL:-}}"
DB_URL="${DB_URL#jdbc:}"
if [[ -z "$DB_URL" ]]; then
  : "${POSTGRES_DB:?PSQL_DATABASE_URL or DATABASE_URL is required}"
  DB_URL="postgresql://${POSTGRES_HOST:-localhost}:${POSTGRES_PORT:-5432}/${POSTGRES_DB}"
fi
export PGUSER="${PGUSER:-${POSTGRES_USER:-}}"
export PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:-}}"

# Deliberately list every table so the foreign-key order remains visible.
psql --set ON_ERROR_STOP=1 --dbname="$DB_URL" <<'SQL'
BEGIN;

DELETE FROM revision_control;
DELETE FROM ficha_foto;
DELETE FROM pago;
DELETE FROM repuesto_pedido_item;
DELETE FROM service_moto;
DELETE FROM auditoria;
DELETE FROM refresh_token;
DELETE FROM revision;
DELETE FROM repuesto_pedido;
DELETE FROM transferencia_moto;
DELETE FROM propietario_moto;
DELETE FROM ficha_trabajo;
DELETE FROM ficha;
DELETE FROM motovehiculo;
DELETE FROM cliente;

SELECT setval('ficha_numero_seq', 1, false);
SELECT setval('repuesto_pedido_numero_seq', 1, false);

COMMIT;
SQL
