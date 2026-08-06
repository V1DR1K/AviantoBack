CREATE TABLE control_entrega_catalogo (
  id UUID PRIMARY KEY,
  nombre VARCHAR(200) NOT NULL,
  descripcion TEXT,
  obligatorio BOOLEAN NOT NULL DEFAULT TRUE,
  orden INTEGER NOT NULL DEFAULT 0,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_control_entrega_nombre ON control_entrega_catalogo (lower(nombre)) WHERE deleted_at IS NULL;

CREATE TABLE revision_entrega (
  id UUID PRIMARY KEY,
  ficha_id UUID NOT NULL UNIQUE REFERENCES pedido,
  estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
  aprobado_por UUID,
  aprobado_at TIMESTAMPTZ,
  forzada BOOLEAN NOT NULL DEFAULT FALSE,
  observacion TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);

CREATE TABLE revision_entrega_item (
  id UUID PRIMARY KEY,
  revision_entrega_id UUID NOT NULL REFERENCES revision_entrega,
  control_id UUID NOT NULL REFERENCES control_entrega_catalogo,
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  observacion TEXT,
  correccion_necesaria TEXT,
  revisado_por UUID,
  revisado_at TIMESTAMPTZ
);