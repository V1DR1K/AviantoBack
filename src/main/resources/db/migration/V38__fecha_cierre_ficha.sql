ALTER TABLE ficha ADD COLUMN fecha_cierre TIMESTAMPTZ;

UPDATE ficha
SET fecha_cierre = created_at + INTERVAL '7 days'
WHERE fecha_cierre IS NULL;

ALTER TABLE ficha ALTER COLUMN fecha_cierre SET NOT NULL;
