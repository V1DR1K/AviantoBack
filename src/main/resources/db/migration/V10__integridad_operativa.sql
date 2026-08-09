-- Reglas que protegen los invariantes operativos incluso ante accesos directos a la base.

CREATE UNIQUE INDEX ux_ficha_moto_abierta
  ON ficha (motovehiculo_id)
  WHERE deleted_at IS NULL AND estado NOT IN ('ENTREGADA', 'CANCELADA');

ALTER TABLE ficha
  ADD CONSTRAINT ck_ficha_descuento_global_nonnegative CHECK (descuento_global >= 0),
  ADD CONSTRAINT ck_ficha_total_nonnegative CHECK (total >= 0),
  ADD CONSTRAINT ck_ficha_kilometraje_nonnegative CHECK (kilometraje_ingreso IS NULL OR kilometraje_ingreso >= 0);

ALTER TABLE ficha_trabajo
  ADD CONSTRAINT ck_ficha_trabajo_importes_nonnegative CHECK (precio_aplicado >= 0 AND descuento >= 0 AND subtotal >= 0),
  ADD CONSTRAINT ck_ficha_trabajo_subtotal CHECK (subtotal = precio_aplicado - descuento);

ALTER TABLE service_moto
  ADD CONSTRAINT ck_service_moto_kilometraje_nonnegative CHECK (kilometraje >= 0);

ALTER TABLE repuesto_pedido
  ADD CONSTRAINT ck_repuesto_pedido_total_nonnegative CHECK (total >= 0);

ALTER TABLE repuesto_pedido_item
  ADD CONSTRAINT ck_repuesto_item_valores_validos CHECK (cantidad > 0 AND precio >= 0 AND subtotal >= 0),
  ADD CONSTRAINT ck_repuesto_item_subtotal CHECK (subtotal = cantidad * precio);

CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE propietario_moto
  ADD CONSTRAINT ex_propietario_moto_periodo
  EXCLUDE USING gist (
    motovehiculo_id WITH =,
    daterange(fecha_desde, COALESCE(fecha_hasta, 'infinity'::date), '[]') WITH &&
  ) WHERE (deleted_at IS NULL);
