ALTER TABLE ficha_venta
  DROP CONSTRAINT IF EXISTS ficha_venta_motovehiculo_id_key,
  ADD COLUMN cancelada_at TIMESTAMPTZ,
  ADD COLUMN cancelada_por UUID REFERENCES app_user,
  ADD COLUMN cancelada_motivo VARCHAR(500);

CREATE INDEX ix_ficha_venta_cancelada
  ON ficha_venta (cancelada_at) WHERE cancelada_at IS NOT NULL;

CREATE UNIQUE INDEX ux_ficha_venta_motovehiculo_activa
  ON ficha_venta (motovehiculo_id) WHERE deleted_at IS NULL AND cancelada_at IS NULL;
