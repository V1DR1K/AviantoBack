INSERT INTO marca_moto (id,nombre,activo,created_at,updated_at) VALUES
('7aa00000-0000-0000-0000-000000000001','Suzuki',true,now(),now()),
('7aa00000-0000-0000-0000-000000000002','KTM',true,now(),now()),
('7aa00000-0000-0000-0000-000000000003','Motomel',true,now(),now()),
('7aa00000-0000-0000-0000-000000000004','Gilera',true,now(),now()),
('7aa00000-0000-0000-0000-000000000005','Corven',true,now(),now()),
('7aa00000-0000-0000-0000-000000000006','Zanella',true,now(),now()),
('7aa00000-0000-0000-0000-000000000007','Mondial',true,now(),now()),
('7aa00000-0000-0000-0000-000000000008','Guerrero',true,now(),now());

INSERT INTO categoria_catalogo (id,nombre,activo,created_at,updated_at) VALUES
('7bb00000-0000-0000-0000-000000000001','Service',true,now(),now());

INSERT INTO item_catalogo (id,descripcion,tipo,categoria_id,precio_base,activo,created_at,updated_at) VALUES
('88888888-0000-0000-0000-000000000001','Cambio de aceite','Trabajo','55555555-5555-5555-5555-555555555555',13000,true,now(),now()),
('88888888-0000-0000-0000-000000000002','Service integral','Trabajo','7bb00000-0000-0000-0000-000000000001',32000,true,now(),now()),
('88888888-0000-0000-0000-000000000003','Reglaje de frenos','Trabajo','55555555-5555-5555-5555-555555555555',9000,true,now(),now()),
('88888888-0000-0000-0000-000000000004','Filtro de aire','Pieza','44444444-4444-4444-4444-444444444444',6800,true,now(),now()),
('88888888-0000-0000-0000-000000000005','Bujía','Pieza','44444444-4444-4444-4444-444444444444',9500,true,now(),now()),
('88888888-0000-0000-0000-000000000006','Pastillas de freno','Pieza','44444444-4444-4444-4444-444444444444',12500,true,now(),now()),
('88888888-0000-0000-0000-000000000007','Cubierta','Pieza','44444444-4444-4444-4444-444444444444',78000,true,now(),now()),
('88888888-0000-0000-0000-000000000008','Cadena de transmisión','Pieza','44444444-4444-4444-4444-444444444444',25000,true,now(),now()),
('88888888-0000-0000-0000-000000000009','Espejo retrovisor','Pieza','66666666-6666-6666-6666-666666666666',15000,true,now(),now());

INSERT INTO control_entrega_catalogo (id,nombre,descripcion,obligatorio,orden,activo,created_at,updated_at) VALUES
('99999999-0000-0000-0000-000000000001','Revisar luces','Alta, baja, guiños y stop funcionando',true,1,true,now(),now()),
('99999999-0000-0000-0000-000000000002','Verificar frenos','Freno delantero y trasero regulados y sin fuga.',true,2,true,now(),now()),
('99999999-0000-0000-0000-000000000003','Controlar presión de neumáticos','Presión según carga y desgaste de cubiertas.',true,3,true,now(),now()),
('99999999-0000-0000-0000-000000000004','Revisar pérdidas','Verificar fugas de líquidos y aceite.',true,4,true,now(),now()),
('99999999-0000-0000-0000-000000000005','Confirmar trabajos realizados','Cotejar que todos los ítems de la ficha se completaron.',true,5,true,now(),now()),
('99999999-0000-0000-0000-000000000006','Verificar limpieza','Moto limpia y sin herramientas olvidadas.',false,6,true,now(),now()),
('99999999-0000-0000-0000-000000000007','Confirmar documentación','Título, cédula y elementos entregados al cliente.',true,7,true,now(),now());