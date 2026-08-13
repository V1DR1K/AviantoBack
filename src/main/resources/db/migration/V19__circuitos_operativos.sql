ALTER TABLE motovehiculo DROP CONSTRAINT ck_moto_estado_operativo;
ALTER TABLE motovehiculo DROP CONSTRAINT ck_moto_ingreso_coherente;

UPDATE ficha
SET estado = 'PENDIENTE'
WHERE estado = 'CARGA';

UPDATE motovehiculo
SET estado_operativo = 'PENDIENTE'
WHERE estado_operativo = 'CARGADA';

UPDATE motovehiculo
SET estado_operativo = 'TRANSFERENCIA_EN_PROCESO'
WHERE estado_operativo = 'TRANSFERENCIA_EN_CURSO';

ALTER TABLE motovehiculo
  ADD CONSTRAINT ck_moto_estado_operativo CHECK (estado_operativo IN ('DISPONIBLE', 'INGRESADA_TALLER', 'PENDIENTE', 'EN_PROCESO', 'REVISION', 'TERMINADA', 'ENTREGADA', 'EN_VENTA', 'TRANSFERENCIA_EN_PROCESO', 'VENDIDA')),
  ADD CONSTRAINT ck_moto_ingreso_coherente CHECK (
    (estado_operativo IN ('DISPONIBLE', 'ENTREGADA', 'VENDIDA') AND ingresada = FALSE)
    OR (estado_operativo IN ('INGRESADA_TALLER', 'PENDIENTE', 'EN_PROCESO', 'REVISION', 'TERMINADA', 'EN_VENTA', 'TRANSFERENCIA_EN_PROCESO') AND ingresada = TRUE)
  );
