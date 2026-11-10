-- Roles configurables: cada rol guarda un conjunto de permisos (ver Permisos.java).
-- Los dos roles existentes pasan a ser roles de sistema con IDs fijos.
-- ADMINISTRACION: siempre tiene todos los permisos del catálogo (los sincroniza SeedService) y no se edita.
-- OPERARIO: arranca con los permisos que ya tenía; se puede editar desde Gestión > Roles.

CREATE TABLE rol (
  id UUID PRIMARY KEY,
  codigo VARCHAR(40) NOT NULL,
  nombre VARCHAR(80) NOT NULL,
  descripcion VARCHAR(255),
  sistema BOOLEAN NOT NULL DEFAULT FALSE,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_rol_codigo ON rol (codigo);
CREATE UNIQUE INDEX ux_rol_nombre_activo ON rol (lower(nombre)) WHERE deleted_at IS NULL;

CREATE TABLE rol_permiso (
  rol_id UUID NOT NULL REFERENCES rol (id),
  permiso VARCHAR(80) NOT NULL,
  PRIMARY KEY (rol_id, permiso)
);

INSERT INTO rol (id, codigo, nombre, descripcion, sistema, activo, version, created_at, updated_at) VALUES
  ('00000000-0000-0000-0000-000000000001', 'ADMINISTRACION', 'Administración', 'Acceso completo al sistema. No se modifica.', TRUE, TRUE, 0, now(), now()),
  ('00000000-0000-0000-0000-000000000002', 'OPERARIO', 'Operario', 'Operación diaria de taller y ventas.', TRUE, TRUE, 0, now(), now());

INSERT INTO rol_permiso (rol_id, permiso) VALUES
  ('00000000-0000-0000-0000-000000000002', 'menu.inicio'),
  ('00000000-0000-0000-0000-000000000002', 'menu.perfiles'),
  ('00000000-0000-0000-0000-000000000002', 'menu.wiki'),
  ('00000000-0000-0000-0000-000000000002', 'menu.fichas'),
  ('00000000-0000-0000-0000-000000000002', 'menu.pedidos'),
  ('00000000-0000-0000-0000-000000000002', 'menu.services'),
  ('00000000-0000-0000-0000-000000000002', 'menu.ventas'),
  ('00000000-0000-0000-0000-000000000002', 'menu.transferencias'),
  ('00000000-0000-0000-0000-000000000002', 'menu.clientes'),
  ('00000000-0000-0000-0000-000000000002', 'menu.motos'),
  ('00000000-0000-0000-0000-000000000002', 'menu.reportes'),
  ('00000000-0000-0000-0000-000000000002', 'dashboard.ver'),
  ('00000000-0000-0000-0000-000000000002', 'clientes.ver'),
  ('00000000-0000-0000-0000-000000000002', 'clientes.crear'),
  ('00000000-0000-0000-0000-000000000002', 'clientes.editar'),
  ('00000000-0000-0000-0000-000000000002', 'motos.ver'),
  ('00000000-0000-0000-0000-000000000002', 'motos.crear'),
  ('00000000-0000-0000-0000-000000000002', 'motos.editar'),
  ('00000000-0000-0000-0000-000000000002', 'motos.ingresar_taller'),
  ('00000000-0000-0000-0000-000000000002', 'motos.circuito'),
  ('00000000-0000-0000-0000-000000000002', 'services.ver'),
  ('00000000-0000-0000-0000-000000000002', 'services.registrar'),
  ('00000000-0000-0000-0000-000000000002', 'services.editar'),
  ('00000000-0000-0000-0000-000000000002', 'fichas.ver'),
  ('00000000-0000-0000-0000-000000000002', 'fichas.crear'),
  ('00000000-0000-0000-0000-000000000002', 'fichas.editar'),
  ('00000000-0000-0000-0000-000000000002', 'fichas.estado'),
  ('00000000-0000-0000-0000-000000000002', 'revision.controlar'),
  ('00000000-0000-0000-0000-000000000002', 'pedidos.ver'),
  ('00000000-0000-0000-0000-000000000002', 'pedidos.crear'),
  ('00000000-0000-0000-0000-000000000002', 'pedidos.editar'),
  ('00000000-0000-0000-0000-000000000002', 'pedidos.estado'),
  ('00000000-0000-0000-0000-000000000002', 'ventas.ver'),
  ('00000000-0000-0000-0000-000000000002', 'ventas.editar'),
  ('00000000-0000-0000-0000-000000000002', 'transferencias.ver'),
  ('00000000-0000-0000-0000-000000000002', 'trabajos.ver'),
  ('00000000-0000-0000-0000-000000000002', 'marcas.ver'),
  ('00000000-0000-0000-0000-000000000002', 'reportes.ver');

ALTER TABLE app_user ADD COLUMN rol_id UUID;
UPDATE app_user SET rol_id = '00000000-0000-0000-0000-000000000001' WHERE rol = 'ADMINISTRACION';
UPDATE app_user SET rol_id = '00000000-0000-0000-0000-000000000002' WHERE rol_id IS NULL;
ALTER TABLE app_user ALTER COLUMN rol_id SET NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_rol FOREIGN KEY (rol_id) REFERENCES rol (id);
ALTER TABLE app_user DROP COLUMN rol;
