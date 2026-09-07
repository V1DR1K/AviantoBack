-- Una moto puede volver al circuito de Ventas después de una venta anterior.
-- Las fichas finalizadas se conservan como historial y las nuevas se registran aparte.
DROP INDEX IF EXISTS ux_ficha_venta_motovehiculo_activa;

CREATE UNIQUE INDEX ux_ficha_venta_motovehiculo_activa
  ON ficha_venta (motovehiculo_id)
  WHERE deleted_at IS NULL AND cancelada_at IS NULL AND finalizada_at IS NULL;
