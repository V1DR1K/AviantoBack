-- Keep the database state names aligned with MotoState and the sale workflow.
ALTER TABLE transferencia_moto
  ALTER COLUMN fecha_transferencia DROP NOT NULL;

ALTER TABLE motovehiculo
  DROP CONSTRAINT IF EXISTS ck_moto_estado_operativo,
  DROP CONSTRAINT IF EXISTS ck_moto_ingreso_coherente;

ALTER TABLE motovehiculo
  ADD CONSTRAINT ck_moto_estado_operativo CHECK (estado_operativo IN ('DISPONIBLE', 'INGRESADA_TALLER', 'PENDIENTE', 'EN_PROCESO', 'REVISION', 'TERMINADA', 'ENTREGADA', 'EN_VENTA', 'TRANSFERENCIA_EN_PROCESO', 'VENDIDA')),
  ADD CONSTRAINT ck_moto_ingreso_coherente CHECK (
    (estado_operativo IN ('DISPONIBLE', 'ENTREGADA', 'VENDIDA') AND ingresada = FALSE)
    OR (estado_operativo IN ('INGRESADA_TALLER', 'PENDIENTE', 'EN_PROCESO', 'REVISION', 'TERMINADA', 'EN_VENTA', 'TRANSFERENCIA_EN_PROCESO') AND ingresada = TRUE)
  );
