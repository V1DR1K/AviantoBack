ALTER TABLE pedido ADD COLUMN fecha_ingreso DATE;
ALTER TABLE pedido ADD COLUMN fecha_entrega_estimada DATE;
ALTER TABLE pedido ADD COLUMN fecha_entrega_real DATE;
ALTER TABLE pedido ADD COLUMN kilometraje_ingreso INTEGER;
ALTER TABLE pedido ADD COLUMN estado_pago VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
UPDATE pedido SET fecha_ingreso = created_at::date WHERE fecha_ingreso IS NULL;

CREATE INDEX ix_pedido_estado_pago ON pedido (estado, estado_pago, fecha_ingreso);

ALTER TABLE pedido_item ADD COLUMN estado_trabajo VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
ALTER TABLE pedido_item ADD COLUMN observacion_trabajo TEXT;
ALTER TABLE pedido_item ADD COLUMN completado_at TIMESTAMPTZ;
ALTER TABLE pedido_item ADD COLUMN completado_por UUID;