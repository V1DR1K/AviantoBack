-- Estado canónico para motos creadas antes del ingreso explícito.
UPDATE motovehiculo
SET estado_operativo = 'DISPONIBLE', ingresada = FALSE, seccion = NULL
WHERE estado_operativo IS NULL OR estado_operativo = 'ACTIVA';

UPDATE motovehiculo
SET estado_operativo = 'DISPONIBLE', ingresada = FALSE, seccion = NULL
WHERE estado_operativo NOT IN ('DISPONIBLE', 'INGRESADA_TALLER', 'CARGADA', 'EN_PROCESO', 'REVISION', 'ENTREGADA', 'INGRESADA_VENTA', 'EN_VENTA', 'TRANSFERENCIA_EN_CURSO', 'VENDIDA');

ALTER TABLE motovehiculo
  ADD CONSTRAINT ck_moto_estado_operativo CHECK (estado_operativo IN ('DISPONIBLE', 'INGRESADA_TALLER', 'CARGADA', 'EN_PROCESO', 'REVISION', 'ENTREGADA', 'INGRESADA_VENTA', 'EN_VENTA', 'TRANSFERENCIA_EN_CURSO', 'VENDIDA')),
  ADD CONSTRAINT ck_moto_ingreso_coherente CHECK (
    (estado_operativo IN ('DISPONIBLE', 'ENTREGADA', 'VENDIDA') AND ingresada = FALSE)
    OR (estado_operativo IN ('INGRESADA_TALLER', 'CARGADA', 'EN_PROCESO', 'REVISION', 'INGRESADA_VENTA', 'EN_VENTA', 'TRANSFERENCIA_EN_CURSO') AND ingresada = TRUE)
  );
ALTER TABLE motovehiculo ALTER COLUMN estado_operativo SET NOT NULL;

-- Optimistic locking for all aggregate roots using BaseEntity.
ALTER TABLE app_user ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE marca_moto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cliente ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE motovehiculo ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE propietario_moto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transferencia_moto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ficha ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE repuesto_pedido ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE trabajo_catalogo ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categoria ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE control_revision ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE revision ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- A revision may contain each control once. Keep the oldest row if bad legacy
-- data contains duplicates, without cascading into other operational tables.
DELETE FROM revision_control duplicate
USING revision_control original
WHERE duplicate.revision_id = original.revision_id
  AND duplicate.control_id = original.control_id
  AND duplicate.ctid > original.ctid;
CREATE UNIQUE INDEX ux_revision_control_revision_control
  ON revision_control (revision_id, control_id);
