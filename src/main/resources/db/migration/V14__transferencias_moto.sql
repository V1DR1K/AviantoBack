CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE transferencia_moto (
  id UUID PRIMARY KEY,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  cliente_anterior_id UUID NOT NULL REFERENCES cliente,
  cliente_nuevo_id UUID NOT NULL REFERENCES cliente,
  fecha_transferencia DATE NOT NULL,
  observaciones TEXT,
  realizada_por UUID REFERENCES app_user,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID,
  CONSTRAINT ck_transferencia_clientes_distintos CHECK (cliente_anterior_id <> cliente_nuevo_id)
);

CREATE INDEX ix_transferencia_moto_fecha ON transferencia_moto (motovehiculo_id, fecha_transferencia DESC);
CREATE INDEX ix_transferencia_fecha ON transferencia_moto (fecha_transferencia DESC);
CREATE INDEX ix_transferencia_clientes ON transferencia_moto (cliente_anterior_id, cliente_nuevo_id);

INSERT INTO transferencia_moto (
  id,
  motovehiculo_id,
  cliente_anterior_id,
  cliente_nuevo_id,
  fecha_transferencia,
  observaciones,
  created_at,
  updated_at
)
SELECT
  gen_random_uuid(),
  history.motovehiculo_id,
  history.cliente_anterior_id,
  history.cliente_nuevo_id,
  history.fecha_desde,
  history.observaciones,
  now(),
  now()
FROM (
  SELECT
    p.motovehiculo_id,
    lag(p.cliente_id) OVER (PARTITION BY p.motovehiculo_id ORDER BY p.fecha_desde, p.created_at) AS cliente_anterior_id,
    p.cliente_id AS cliente_nuevo_id,
    p.fecha_desde,
    p.observaciones
  FROM propietario_moto p
  WHERE p.deleted_at IS NULL AND p.fecha_desde IS NOT NULL
) history
WHERE history.cliente_anterior_id IS NOT NULL
  AND history.cliente_anterior_id <> history.cliente_nuevo_id;
