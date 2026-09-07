# Plan de estabilización y producción de Avianto

Estado actual: **EN CURSO**. Este plan acompaña el código y se actualiza con evidencia, no solamente con intención.

## Alcance aprobado

- MVP de taller seguro: clientes, motos, fichas, trabajos, repuestos, pagos, revisión, entrega y trazabilidad.
- Permisos configurables por acción; Administración conserva control sobre dinero, configuración y bajas.
- Despliegue local mediante un stack Docker completo y reproducible.
- Facturación fiscal, precio/cobro de venta e inventario real quedan fuera de este release y no deben presentarse como disponibles.

## Convenciones

- [ ] Pendiente
- [~] En curso
- [x] Verificado
- [!] Bloqueado

## Fase 0: bloqueo de release

- [x] Registrar la divergencia entre runtime y worktree.
- [x] Evitar Swagger público en producción.
- [x] Evitar replay concurrente de refresh tokens.
- [x] Corregir JPQL inválido de eliminación de categorías.
- [x] Restringir bajas, pagos, anulaciones y entrega a Administración.
- [ ] Verificar V1-V25 en PostgreSQL limpio y V24-V25 sobre una copia representativa.
- [ ] No aplicar V9 sobre una base real sin procedimiento de migración aprobado.

## Fase 1: integridad de negocio

- [x] Impedir reasignar cliente o moto de una ficha existente.
- [x] Sincronizar el estado de la moto al eliminar lógicamente una ficha abierta.
- [x] Impedir reasignar pedidos de repuestos que ya tienen cobros.
- [x] Evitar que pagos históricos de operaciones cerradas bloqueen cambios de circuito.
- [x] Limitar cantidades y precios de repuestos a dos decimales.
- [x] Validar fechas de ingreso, entrega estimada, vencimiento y kilometraje.
- [ ] Reemplazar escrituras implícitas en GET por comandos explícitos.
- [x] Agregar idempotencia a pagos.
- [ ] Agregar locking/versionado de todos los hijos mutables.
- [ ] Persistir snapshots de controles de revisión y venta.

## Fase 2: permisos

- [x] Introducir permisos configurables para Operario mediante `AVIANTO_OPERATOR_PERMISSIONS`.
- [x] Aplicar permisos a cambio de circuito, estado de ficha y controles de revisión.
- [x] Mantener acceso completo de Administración.
- [ ] Completar la matriz endpoint/acción/permiso con pruebas MockMvc.
- [ ] Agregar permisos configurables persistidos si el cliente requiere administrarlos desde la UI.

## Fase 3: frontend y flujos

- [x] Hacer recuperable el arranque cuando falla la red o la renovación de sesión.
- [x] Hacer que “Abrir ficha de venta” navegue a la ficha accionable.
- [x] Corregir navegación de configuración para Operario.
- [x] Corregir foco, Escape y bloqueo de confirmaciones.
- [x] Corregir navegación de teclado del autocomplete.
- [x] Corregir modal de menú móvil y bloqueo de scroll.
- [x] Mantener confirmaciones abiertas hasta completar la mutación.
- [x] Corregir asociación del propietario en edición de motos.
- [~] Cancelar respuestas viejas de listados principales: perfiles, repuestos, transferencias, ventas y fichas. Quedan paneles secundarios y cargas puntuales.
- [ ] Hacer idempotente y reintentable la carga de fotos.
- [ ] Agregar protección de cambios sin guardar a edición de fichas.
- [ ] Completar estados de carga, error, retry y empty.
- [ ] Agregar pruebas E2E de los flujos críticos.

## Fase 4: reportes, auditoría y contratos

- [~] Definir semántica de cada métrica financiera y excluir cancelados/futuros donde corresponda. Resumen y evolución ya excluyen fichas canceladas y fechas futuras; queda validar métricas sobre datos representativos.
- [x] Igualar filtros de pantalla y exportación.
- [x] Implementar o eliminar el parámetro `columns`.
- [x] Paginar auditoría y consultas grandes.
- [~] Registrar entidad, ID, antes/después, motivo y correlación en auditoría. Ya se aplica a cambios de circuito, estados y pagos; quedan eventos legacy de ABM y comandos secundarios.
- [ ] Alinear README, contrato API, wiki y OpenAPI con el código actual.

## Fase 5: Docker local

- [x] Incorporar imagen reproducible del frontend.
- [x] Incorporar Compose completo con PostgreSQL, backend, frontend y proxy.
- [ ] Fijar versiones por digest y publicar el SHA de la release.
- [ ] Ejecutar instalación desde una máquina limpia sin depender de systemd/Nginx del host.
- [ ] Agregar readiness/liveness separados y límites de recursos.
- [ ] Probar rollback entre dos releases sin mezclar bundles.

## Fase 6: backups y recuperación

- [x] Hacer backups atómicos con permisos restrictivos.
- [x] Usar nombres de base configurables.
- [ ] Restaurar con `ON_ERROR_STOP`, estrategia transaccional y aplicación detenida.
- [ ] Ensayar restore en una base aislada y validar Flyway, conteos, fotos y pagos.
- [ ] Documentar el procedimiento para el cliente.

## Gate final

- [ ] Cero P0 abiertos.
- [ ] Cero P1 abiertos.
- [ ] Build backend y frontend exitosos.
- [ ] Tests backend, frontend y E2E exitosos.
- [ ] Migraciones verificadas en PostgreSQL real.
- [ ] Backup restaurado con evidencia.
- [ ] Permisos aprobados por negocio.
- [ ] Stack Docker instalado desde cero.
- [ ] Rollback probado.
