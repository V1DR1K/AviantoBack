CREATE TABLE trabajo_catalogo (
  id UUID PRIMARY KEY,
  descripcion VARCHAR(300) NOT NULL,
  precio_base NUMERIC(14,2) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);

CREATE UNIQUE INDEX ux_trabajo_catalogo_descripcion_active
  ON trabajo_catalogo (lower(descripcion))
  WHERE deleted_at IS NULL;
CREATE INDEX ix_trabajo_catalogo_descripcion
  ON trabajo_catalogo (lower(descripcion));
