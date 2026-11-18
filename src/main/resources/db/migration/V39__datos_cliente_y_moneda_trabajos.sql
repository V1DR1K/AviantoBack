-- El permiso de completar datos de clientes queda habilitado en todos los roles actuales.
INSERT INTO rol_permiso (rol_id, permiso)
SELECT id, 'clientes.completar_datos'
FROM rol
WHERE deleted_at IS NULL
ON CONFLICT (rol_id, permiso) DO NOTHING;

-- Conserva los importes originales y la cotización usada; los campos existentes siguen en ARS.
ALTER TABLE ficha_trabajo
  ADD COLUMN moneda_origen VARCHAR(3) NOT NULL DEFAULT 'ARS',
  ADD COLUMN precio_origen NUMERIC(14, 2),
  ADD COLUMN descuento_origen NUMERIC(14, 2),
  ADD COLUMN cotizacion_usd NUMERIC(18, 6),
  ADD COLUMN referencia_cotizacion VARCHAR(20),
  ADD COLUMN fecha_cotizacion TIMESTAMPTZ;

UPDATE ficha_trabajo
SET precio_origen = precio_aplicado,
    descuento_origen = COALESCE(descuento, 0)
WHERE precio_origen IS NULL OR descuento_origen IS NULL;
