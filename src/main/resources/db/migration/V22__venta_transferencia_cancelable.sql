ALTER TABLE transferencia_moto
  ADD COLUMN cancelada_at TIMESTAMPTZ,
  ADD COLUMN cancelada_por UUID REFERENCES app_user;

-- Ownership ranges are half-open so a sale can change owner on the same date.
ALTER TABLE propietario_moto DROP CONSTRAINT ex_propietario_moto_periodo;
UPDATE propietario_moto
SET fecha_hasta = fecha_hasta + 1
WHERE fecha_hasta IS NOT NULL;
ALTER TABLE propietario_moto
  ADD CONSTRAINT ex_propietario_moto_periodo
  EXCLUDE USING gist (
    motovehiculo_id WITH =,
    daterange(fecha_desde, COALESCE(fecha_hasta, 'infinity'::date), '[)') WITH &&
  ) WHERE (deleted_at IS NULL);
