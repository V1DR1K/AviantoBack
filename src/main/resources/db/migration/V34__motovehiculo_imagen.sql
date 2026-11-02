-- V34: imagen de la moto. La miniatura y la versión completa se guardan en base64 (texto) en una
-- tabla aparte: el listado de perfiles solo lee la marca imagen_actualizada_at y nunca carga imágenes.
ALTER TABLE motovehiculo ADD COLUMN IF NOT EXISTS imagen_actualizada_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS motovehiculo_imagen (
  motovehiculo_id UUID PRIMARY KEY REFERENCES motovehiculo,
  filename VARCHAR(255),
  content_type VARCHAR(40) NOT NULL,
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  miniatura_base64 TEXT NOT NULL,
  completa_base64 TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
