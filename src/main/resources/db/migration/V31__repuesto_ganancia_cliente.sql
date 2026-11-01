-- Precio con ganancia en repuestos: el precio cargado es el costo; el cliente paga costo + porcentaje.
-- Los ítems históricos quedan con 0% para que sus totales y pagos no cambien.
ALTER TABLE repuesto_pedido_item
  ADD COLUMN IF NOT EXISTS porcentaje_ganancia NUMERIC(5,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS precio_venta NUMERIC(14,2),
  ADD COLUMN IF NOT EXISTS subtotal_venta NUMERIC(14,2);

UPDATE repuesto_pedido_item
   SET precio_venta = precio,
       subtotal_venta = subtotal
 WHERE precio_venta IS NULL OR subtotal_venta IS NULL;

ALTER TABLE repuesto_pedido_item
  ALTER COLUMN precio_venta SET NOT NULL,
  ALTER COLUMN subtotal_venta SET NOT NULL,
  ADD CONSTRAINT ck_repuesto_item_ganancia CHECK (
    porcentaje_ganancia >= 0 AND precio_venta >= 0 AND subtotal_venta >= 0
  );

-- El teléfono deja de ser obligatorio; el DNI pasa a exigirse desde la aplicación.
ALTER TABLE cliente ALTER COLUMN telefono DROP NOT NULL;
