CREATE TABLE pedido_repuesto (
  id UUID PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  cliente_id UUID NOT NULL REFERENCES cliente,
  ficha_id UUID REFERENCES pedido,
  fecha DATE,
  estado VARCHAR(20) NOT NULL DEFAULT 'EN_CURSO',
  estado_pago VARCHAR(20) NOT NULL DEFAULT 'NO_PAGADO',
  total NUMERIC(14,2) NOT NULL DEFAULT 0,
  proveedor VARCHAR(160),
  observaciones TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE INDEX ix_pedido_repuesto_moto ON pedido_repuesto (motovehiculo_id, fecha);
CREATE INDEX ix_pedido_repuesto_estado ON pedido_repuesto (estado, estado_pago);

CREATE TABLE pedido_repuesto_item (
  id UUID PRIMARY KEY,
  pedido_repuesto_id UUID NOT NULL REFERENCES pedido_repuesto,
  descripcion VARCHAR(300) NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  cantidad NUMERIC(14,2) NOT NULL,
  precio NUMERIC(14,2) NOT NULL,
  subtotal NUMERIC(14,2) NOT NULL,
  estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_DE_PEDIR',
  observaciones TEXT
);