ALTER TABLE ficha_trabajo
  ADD COLUMN pagado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE repuesto_pedido_item
  ADD COLUMN pagado BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE ficha_trabajo t
SET pagado = TRUE
FROM ficha f
WHERE f.id = t.ficha_id
  AND f.estado_pago = 'PAGADO';

UPDATE ficha
SET estado_pago = 'NO_PAGADO'
WHERE estado_pago = 'PARCIAL';

UPDATE repuesto_pedido_item i
SET pagado = TRUE
FROM repuesto_pedido p
WHERE p.id = i.repuesto_pedido_id
  AND p.estado_pago = 'PAGADO';

UPDATE repuesto_pedido
SET estado_pago = 'NO_PAGADO'
WHERE estado_pago = 'PARCIAL';
