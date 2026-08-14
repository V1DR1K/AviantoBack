CREATE TABLE pago (
  id UUID PRIMARY KEY,
  ficha_id UUID REFERENCES ficha,
  repuesto_pedido_id UUID REFERENCES repuesto_pedido,
  monto NUMERIC(14,2) NOT NULL CHECK (monto > 0),
  fecha DATE NOT NULL,
  medio_pago VARCHAR(30),
  creado_por UUID REFERENCES app_user,
  anulado_at TIMESTAMPTZ,
  anulado_por UUID REFERENCES app_user,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_pago_documento CHECK (
    (ficha_id IS NOT NULL AND repuesto_pedido_id IS NULL)
    OR (ficha_id IS NULL AND repuesto_pedido_id IS NOT NULL)
  ),
  CONSTRAINT ck_pago_medio CHECK (medio_pago IS NULL OR medio_pago IN ('EFECTIVO', 'TRANSFERENCIA', 'DEBITO', 'CREDITO', 'MERCADO_PAGO', 'OTRO'))
);

CREATE INDEX ix_pago_ficha_fecha ON pago (ficha_id, fecha DESC) WHERE ficha_id IS NOT NULL;
CREATE INDEX ix_pago_repuesto_fecha ON pago (repuesto_pedido_id, fecha DESC) WHERE repuesto_pedido_id IS NOT NULL;

ALTER TABLE ficha_trabajo DROP COLUMN pagado;
ALTER TABLE repuesto_pedido_item DROP COLUMN pagado;
