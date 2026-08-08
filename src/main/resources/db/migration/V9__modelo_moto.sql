-- V9: Modelo "Moto primero"
-- Destructivo (datos ficticios): reemplaza Pedido/PedidoItem/Catalogo/ControlEntrega por
-- Ficha (solo trabajos cobrables), FichaTrabajo (snapshot precio), RepuestoPedido (piezas/accesorios),
-- Revision (checks finales con categoria N:N). Motovehiculo sin cliente_id ni estado
-- (propietario via propietario_moto, estado derivado de ficha activa).

DROP TABLE IF EXISTS revision_entrega_item, revision_entrega, control_entrega_catalogo,
  pedido_repuesto_item, pedido_repuesto, service_moto, propietario_moto,
  pedido_foto, pedido_item, pedido, price_history, item_catalogo, categoria_catalogo,
  motovehiculo, cliente, marca_moto, app_user, refresh_token, auditoria CASCADE;

-- Entidades base -----------------------------------------------------------

CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  username VARCHAR(120) NOT NULL,
  nombre VARCHAR(120) NOT NULL,
  email VARCHAR(254),
  password_hash VARCHAR(100) NOT NULL,
  rol VARCHAR(30) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_user_username_active ON app_user (lower(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_email_active ON app_user (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE marca_moto (
  id UUID PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_marca_nombre_active ON marca_moto (lower(nombre)) WHERE deleted_at IS NULL;

CREATE TABLE cliente (
  id UUID PRIMARY KEY,
  nombre VARCHAR(160) NOT NULL,
  documento VARCHAR(50),
  telefono VARCHAR(80) NOT NULL,
  email VARCHAR(254),
  direccion VARCHAR(300),
  observaciones TEXT,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_cliente_documento_active ON cliente (lower(documento)) WHERE documento IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_cliente_nombre ON cliente (lower(nombre));

CREATE TABLE motovehiculo (
  id UUID PRIMARY KEY,
  marca_id UUID NOT NULL REFERENCES marca_moto,
  modelo VARCHAR(120) NOT NULL,
  patente VARCHAR(20) NOT NULL,
  anio INTEGER,
  kilometraje INTEGER,
  km_ultimo_service INTEGER,
  fecha_ultimo_service DATE,
  km_service_periodo INTEGER NOT NULL DEFAULT 5000,
  meses_service_periodo INTEGER NOT NULL DEFAULT 6,
  service_observaciones TEXT,
  observaciones TEXT,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_moto_patente_active ON motovehiculo (upper(patente)) WHERE deleted_at IS NULL;

CREATE TABLE propietario_moto (
  id UUID PRIMARY KEY,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  cliente_id UUID NOT NULL REFERENCES cliente,
  fecha_desde DATE,
  fecha_hasta DATE,
  observaciones TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_propietario_moto_actual ON propietario_moto (motovehiculo_id) WHERE fecha_hasta IS NULL AND deleted_at IS NULL;

-- Fichas (solo trabajos cobrables) ----------------------------------------

CREATE TABLE ficha (
  id UUID PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  cliente_id UUID NOT NULL REFERENCES cliente,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  vencimiento DATE,
  fecha_ingreso DATE,
  fecha_entrega_estimada DATE,
  fecha_entrega_real DATE,
  kilometraje_ingreso INTEGER,
  observaciones TEXT,
  descuento_global NUMERIC(14,2) NOT NULL DEFAULT 0,
  iva BOOLEAN NOT NULL DEFAULT FALSE,
  estado VARCHAR(30) NOT NULL DEFAULT 'CARGA',
  estado_pago VARCHAR(20) NOT NULL DEFAULT 'NO_PAGADO',
  total NUMERIC(14,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE INDEX ix_ficha_estado_fecha ON ficha (estado, created_at);
CREATE INDEX ix_ficha_numero ON ficha (numero);
CREATE INDEX ix_ficha_estado_pago ON ficha (estado, estado_pago, fecha_ingreso);

CREATE TABLE ficha_trabajo (
  id UUID PRIMARY KEY,
  ficha_id UUID NOT NULL REFERENCES ficha,
  descripcion VARCHAR(300) NOT NULL,
  precio_aplicado NUMERIC(14,2) NOT NULL,
  descuento NUMERIC(14,2) NOT NULL DEFAULT 0,
  subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
  estado_trabajo VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
  observacion_trabajo TEXT,
  completado_at TIMESTAMPTZ,
  completado_por UUID
);
CREATE INDEX ix_ficha_trabajo_ficha ON ficha_trabajo (ficha_id);

CREATE TABLE ficha_foto (
  id UUID PRIMARY KEY,
  ficha_id UUID NOT NULL REFERENCES ficha,
  filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  content BYTEA NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ficha_foto_ficha ON ficha_foto (ficha_id);

-- Repuestos / accesorios (vinculados a ficha y opcional a trabajo) --------

CREATE TABLE repuesto_pedido (
  id UUID PRIMARY KEY,
  numero VARCHAR(30) NOT NULL UNIQUE,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  cliente_id UUID NOT NULL REFERENCES cliente,
  ficha_id UUID REFERENCES ficha,
  fecha DATE,
  estado VARCHAR(30) NOT NULL DEFAULT 'EN_CURSO',
  estado_pago VARCHAR(20) NOT NULL DEFAULT 'NO_PAGADO',
  total NUMERIC(14,2) NOT NULL DEFAULT 0,
  proveedor VARCHAR(160),
  observaciones TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE INDEX ix_repuesto_pedido_moto ON repuesto_pedido (motovehiculo_id, fecha);
CREATE INDEX ix_repuesto_pedido_estado ON repuesto_pedido (estado, estado_pago);

CREATE TABLE repuesto_pedido_item (
  id UUID PRIMARY KEY,
  repuesto_pedido_id UUID NOT NULL REFERENCES repuesto_pedido,
  ficha_trabajo_id UUID REFERENCES ficha_trabajo,
  descripcion VARCHAR(300) NOT NULL,
  tipo VARCHAR(20) NOT NULL DEFAULT 'REPUESTO',
  cantidad NUMERIC(14,2) NOT NULL DEFAULT 1,
  precio NUMERIC(14,2) NOT NULL,
  subtotal NUMERIC(14,2) NOT NULL,
  estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_DE_PEDIR',
  observaciones TEXT
);
CREATE INDEX ix_repuesto_pedido_item_pedido ON repuesto_pedido_item (repuesto_pedido_id);

-- Revision final (checks con categorias N:M) ------------------------------

CREATE TABLE categoria (
  id UUID PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  deleted_by UUID
);
CREATE UNIQUE INDEX ux_categoria_nombre_active ON categoria (lower(nombre)) WHERE deleted_at IS NULL;

CREATE TABLE control_revision (
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
CREATE UNIQUE INDEX ux_control_revision_nombre ON control_revision (lower(nombre)) WHERE deleted_at IS NULL;

CREATE TABLE control_revision_categoria (
  control_revision_id UUID NOT NULL REFERENCES control_revision,
  categoria_id UUID NOT NULL REFERENCES categoria,
  PRIMARY KEY (control_revision_id, categoria_id)
);

CREATE TABLE revision (
  id UUID PRIMARY KEY,
  ficha_id UUID NOT NULL UNIQUE REFERENCES ficha,
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

CREATE TABLE revision_control (
  id UUID PRIMARY KEY,
  revision_id UUID NOT NULL REFERENCES revision,
  control_id UUID NOT NULL REFERENCES control_revision,
  estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
  observacion TEXT,
  correccion_necesaria TEXT,
  revisado_por UUID,
  revisado_at TIMESTAMPTZ
);
CREATE INDEX ix_revision_control_revision ON revision_control (revision_id);

-- Service y trazabilidad ---------------------------------------------------

CREATE TABLE service_moto (
  id UUID PRIMARY KEY,
  motovehiculo_id UUID NOT NULL REFERENCES motovehiculo,
  ficha_id UUID REFERENCES ficha,
  kilometraje INTEGER NOT NULL,
  fecha DATE NOT NULL,
  observaciones TEXT,
  realizado_por UUID,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_service_moto_moto ON service_moto (motovehiculo_id, fecha DESC);

CREATE TABLE refresh_token (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE auditoria (
  id UUID PRIMARY KEY,
  fecha TIMESTAMPTZ NOT NULL,
  usuario_id UUID REFERENCES app_user,
  modulo VARCHAR(60) NOT NULL,
  accion VARCHAR(60) NOT NULL,
  descripcion TEXT NOT NULL
);

-- Seeds de referencia ------------------------------------------------------

INSERT INTO marca_moto (id,nombre,activo,created_at,updated_at) VALUES
('11111111-1111-1111-1111-111111111111','Honda',true,now(),now()),
('22222222-2222-2222-2222-222222222222','Yamaha',true,now(),now()),
('33333333-3333-3333-3333-333333333333','Bajaj',true,now(),now()),
('7aa00000-0000-0000-0000-000000000004','Gilera',true,now(),now()),
('7aa00000-0000-0000-0000-000000000005','Corven',true,now(),now()),
('7aa00000-0000-0000-0000-000000000006','Zanella',true,now(),now()),
('7aa00000-0000-0000-0000-000000000007','Mondial',true,now(),now()),
('7aa00000-0000-0000-0000-000000000008','Guerrero',true,now(),now());

INSERT INTO categoria (id,nombre,activo,created_at,updated_at) VALUES
('7cc00000-0000-0000-0000-000000000001','General',true,now(),now()),
('7cc00000-0000-0000-0000-000000000002','Frenos',true,now(),now()),
('7cc00000-0000-0000-0000-000000000003','Ruedas y neumaticos',true,now(),now()),
('7cc00000-0000-0000-0000-000000000004','Aceite y liquidos',true,now(),now()),
('7cc00000-0000-0000-0000-000000000005','Electrico y luces',true,now(),now()),
('7cc00000-0000-0000-0000-000000000006','Documentacion',true,now(),now());

INSERT INTO control_revision (id,nombre,descripcion,obligatorio,orden,activo,created_at,updated_at) VALUES
('99999999-0000-0000-0000-000000000001','Revisar luces','Alta, baja, guiños y stop funcionando',true,1,true,now(),now()),
('99999999-0000-0000-0000-000000000002','Frenos delanteros','Freno delantero regulado y sin fuga',true,2,true,now(),now()),
('99999999-0000-0000-0000-000000000003','Frenos traseros','Freno trasero regulado y sin fuga',true,3,true,now(),now()),
('99999999-0000-0000-0000-000000000004','Presion de neumaticos','Presion segun carga y desgaste de cubiertas',true,4,true,now(),now()),
('99999999-0000-0000-0000-000000000005','Pérdidas de liquidos','Verificar fugas de aceite, frenos y refrigerante',true,5,true,now(),now()),
('99999999-0000-0000-0000-000000000006','Confirmar trabajos realizados','Cotejar que todos los trabajos de la ficha se completaron',true,6,true,now(),now()),
('99999999-0000-0000-0000-000000000007','Verificar limpieza','Moto limpia y sin herramientas olvidadas',false,7,true,now(),now()),
('99999999-0000-0000-0000-000000000008','Confirmar documentacion','Titulo, cedula y elementos entregados al cliente',true,8,true,now(),now()),
('99999999-0000-0000-0000-000000000009','Espejos y accesorios','Espejos, patente y accesorios en condiciones',true,9,true,now(),now()),
('99999999-0000-0000-0000-000000000010','Bateria y arranque','Bateria, arranque y embrague en condiciones',true,10,true,now(),now());

INSERT INTO control_revision_categoria (control_revision_id,categoria_id) VALUES
('99999999-0000-0000-0000-000000000001','7cc00000-0000-0000-0000-000000000005'),
('99999999-0000-0000-0000-000000000002','7cc00000-0000-0000-0000-000000000002'),
('99999999-0000-0000-0000-000000000003','7cc00000-0000-0000-0000-000000000002'),
('99999999-0000-0000-0000-000000000004','7cc00000-0000-0000-0000-000000000003'),
('99999999-0000-0000-0000-000000000005','7cc00000-0000-0000-0000-000000000004'),
('99999999-0000-0000-0000-000000000006','7cc00000-0000-0000-0000-000000000001'),
('99999999-0000-0000-0000-000000000007','7cc00000-0000-0000-0000-000000000001'),
('99999999-0000-0000-0000-000000000008','7cc00000-0000-0000-0000-000000000006'),
('99999999-0000-0000-0000-000000000009','7cc00000-0000-0000-0000-000000000001'),
('99999999-0000-0000-0000-000000000010','7cc00000-0000-0000-0000-000000000005');