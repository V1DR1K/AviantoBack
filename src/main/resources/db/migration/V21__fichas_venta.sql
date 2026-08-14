CREATE SEQUENCE ficha_venta_numero_seq START WITH 1;

CREATE TABLE venta_checklist_plantilla (
  id UUID PRIMARY KEY,
  etiqueta VARCHAR(200) NOT NULL,
  orden INTEGER NOT NULL CHECK (orden >= 0),
  obligatorio BOOLEAN NOT NULL DEFAULT TRUE,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_venta_checklist_plantilla_orden ON venta_checklist_plantilla (activo, orden, etiqueta) WHERE deleted_at IS NULL;

CREATE TABLE ficha_venta (
  id UUID PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  motovehiculo_id UUID NOT NULL UNIQUE REFERENCES motovehiculo,
  vendedor_id UUID NOT NULL REFERENCES cliente,
  comprador_id UUID REFERENCES cliente,
  finalizada_at TIMESTAMPTZ,
  finalizada_por UUID REFERENCES app_user,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_ficha_venta_comprador ON ficha_venta (comprador_id) WHERE deleted_at IS NULL;

CREATE TABLE ficha_venta_item (
  id UUID PRIMARY KEY,
  ficha_venta_id UUID NOT NULL REFERENCES ficha_venta,
  etiqueta VARCHAR(200) NOT NULL,
  orden INTEGER NOT NULL CHECK (orden >= 0),
  obligatorio BOOLEAN NOT NULL DEFAULT TRUE,
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'REALIZADO', 'NO_APLICA')),
  realizado_at TIMESTAMPTZ,
  realizado_por UUID REFERENCES app_user,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ficha_venta_item_ficha ON ficha_venta_item (ficha_venta_id, orden);

ALTER TABLE transferencia_moto
  ALTER COLUMN fecha_transferencia DROP NOT NULL,
  ADD COLUMN ficha_venta_id UUID UNIQUE REFERENCES ficha_venta,
  ADD COLUMN cita_fecha DATE,
  ADD COLUMN cita_hora TIME,
  ADD COLUMN cita_lugar VARCHAR(300),
  ADD COLUMN asistencia_at TIMESTAMPTZ,
  ADD COLUMN asistencia_por UUID REFERENCES app_user,
  ADD COLUMN finalizada_at TIMESTAMPTZ,
  ADD COLUMN finalizada_por UUID REFERENCES app_user,
  ADD CONSTRAINT ck_transferencia_cita_completa CHECK (
    (cita_fecha IS NULL AND cita_hora IS NULL AND cita_lugar IS NULL)
    OR (cita_fecha IS NOT NULL AND cita_hora IS NOT NULL AND cita_lugar IS NOT NULL)
  );
CREATE INDEX ix_transferencia_ficha_venta ON transferencia_moto (ficha_venta_id) WHERE ficha_venta_id IS NOT NULL;
