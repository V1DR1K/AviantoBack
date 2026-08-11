ALTER TABLE motovehiculo
  ADD COLUMN ingresada BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN seccion VARCHAR(20),
  ADD COLUMN estado_operativo VARCHAR(40);

WITH latest_ficha AS (
  SELECT DISTINCT ON (motovehiculo_id)
    motovehiculo_id,
    estado
  FROM ficha
  WHERE deleted_at IS NULL
  ORDER BY motovehiculo_id, fecha_ingreso DESC NULLS LAST, created_at DESC
)
UPDATE motovehiculo m
SET seccion = 'TALLER',
    ingresada = latest.estado NOT IN ('ENTREGADA', 'CANCELADA'),
    estado_operativo = CASE latest.estado
      WHEN 'CARGA' THEN CASE WHEN latest.estado NOT IN ('ENTREGADA', 'CANCELADA') THEN 'CARGADA' ELSE 'ENTREGADA' END
      WHEN 'EN_PROCESO' THEN 'EN_PROCESO'
      WHEN 'REVISION' THEN 'REVISION'
      WHEN 'ENTREGADA' THEN 'ENTREGADA'
      WHEN 'CANCELADA' THEN 'ENTREGADA'
      ELSE NULL
    END
FROM latest_ficha latest
WHERE m.id = latest.motovehiculo_id;

UPDATE motovehiculo m
SET seccion = 'TALLER',
    ingresada = TRUE,
    estado_operativo = 'INGRESADA_TALLER'
WHERE m.deleted_at IS NULL
  AND EXISTS (
    SELECT 1
    FROM repuesto_pedido r
    WHERE r.motovehiculo_id = m.id
      AND r.deleted_at IS NULL
      AND r.estado = 'EN_CURSO'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM ficha f
    WHERE f.motovehiculo_id = m.id
      AND f.deleted_at IS NULL
      AND f.estado NOT IN ('ENTREGADA', 'CANCELADA')
  );

CREATE INDEX ix_motovehiculo_seccion_estado
  ON motovehiculo (seccion, estado_operativo, ingresada);
