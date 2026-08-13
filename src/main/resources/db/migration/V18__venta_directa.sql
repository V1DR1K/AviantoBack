UPDATE motovehiculo
SET estado_operativo = 'EN_VENTA',
    ingresada = TRUE,
    seccion = 'VENTA'
WHERE estado_operativo = 'INGRESADA_VENTA';

ALTER TABLE motovehiculo DROP CONSTRAINT ck_moto_estado_operativo;
ALTER TABLE motovehiculo DROP CONSTRAINT ck_moto_ingreso_coherente;

ALTER TABLE motovehiculo
  ADD CONSTRAINT ck_moto_estado_operativo CHECK (estado_operativo IN ('DISPONIBLE', 'INGRESADA_TALLER', 'CARGADA', 'EN_PROCESO', 'REVISION', 'ENTREGADA', 'EN_VENTA', 'TRANSFERENCIA_EN_CURSO', 'VENDIDA')),
  ADD CONSTRAINT ck_moto_ingreso_coherente CHECK (
    (estado_operativo IN ('DISPONIBLE', 'ENTREGADA', 'VENDIDA') AND ingresada = FALSE)
    OR (estado_operativo IN ('INGRESADA_TALLER', 'CARGADA', 'EN_PROCESO', 'REVISION', 'EN_VENTA', 'TRANSFERENCIA_EN_CURSO') AND ingresada = TRUE)
  );
