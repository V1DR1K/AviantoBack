ALTER TABLE motovehiculo ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA';
ALTER TABLE motovehiculo ADD COLUMN km_ultimo_service INTEGER;
ALTER TABLE motovehiculo ADD COLUMN fecha_ultimo_service DATE;
ALTER TABLE motovehiculo ADD COLUMN km_service_periodo INTEGER NOT NULL DEFAULT 5000;
ALTER TABLE motovehiculo ADD COLUMN meses_service_periodo INTEGER NOT NULL DEFAULT 6;
ALTER TABLE motovehiculo ADD COLUMN service_observaciones TEXT;

CREATE TABLE propietario_moto (
  id UUID PRIMARY KEY,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  cliente_id UUID NOT NULL REFERENCES cliente,
  fecha_desde DATE,
  fecha_hasta DATE,
  observaciones TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_propietario_moto_actual ON propietario_moto (motovehiculo_id) WHERE fecha_hasta IS NULL AND deleted_at IS NULL;

CREATE TABLE service_moto (
  id UUID PRIMARY KEY,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  ficha_id UUID REFERENCES pedido,
  kilometraje INTEGER NOT NULL,
  fecha DATE NOT NULL,
  observaciones TEXT,
  realizado_por UUID,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_service_moto_moto ON service_moto (motovehiculo_id, fecha DESC);