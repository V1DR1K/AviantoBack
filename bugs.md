# Auditoría de Avianto — 2026-10-26

## Alcance y evidencia

- Se auditó `https://avianto.neticar.com.ar/app`. La persona responsable confirmó que es el ambiente de pruebas y autorizó altas, cambios, pagos, transferencias y bajas. Se usaron registros identificables con el prefijo `QA-AUDIT-20261026-A`; se dejaron allí los datos resultantes, conforme a esa autorización. No se guardaron credenciales en este informe.
- El controlador contiene 113 mappings: 48 GET, 31 POST, 14 PUT, 8 PATCH y 12 DELETE. El inventario exacto está al final de este archivo.
- Se invocaron los 48 GET. Se comprobaron también lecturas con datos creados para la auditoría: historial de service, foto WebP, PDF de ficha, pagos, trabajos, repuestos vinculados, pedidos y sus pagos, ventas y transferencias. Las cinco exportaciones XLSX y el PDF devolvieron archivos con firmas válidas. La precondición de consultar una revisión fuera del estado «En revisión» respondió `409`, como corresponde.
- Se repitieron los 48 GET sin token; todos respondieron `403`, sin exponer listados ni detalles a usuarios anónimos.
- Se completaron recorridos válidos para las 62 operaciones de negocio no GET con Administración: altas, actualizaciones parciales y completas, cambios de estado, pagos y anulaciones, transferencias y cancelación, revisiones, entregas y bajas. En cuatro suites se registraron 150 respuestas exitosas (`200`, `201` o `204`) sin fallos de prueba.
- Se validó el rol Operario: las seis lecturas administrativas devolvieron `403`; 16 operaciones reservadas a Administración también fueron denegadas. El Operario sí pudo ingresar una moto al taller, cambiar el circuito, actualizar una ficha y sus trabajos, preparar una revisión y editar sus controles. La aprobación de revisión fue denegada con `403`.
- Se probaron las 62 operaciones de negocio sin token, con cuerpos malformados e IDs inexistentes: las 62 respondieron `403` antes de cualquier cambio. Se enviaron cuerpos vacíos a 33 operaciones que los validan (todas rechazaron con `400`); las 43 rutas con cuerpo rechazaron JSON malformado con `400`, y las 22 rutas sin cuerpo rechazaron UUID malformados con `400`. Login, renovación y logout también se completaron correctamente.
- En el navegador se recorrieron Inicio, Perfiles, Fichas y detalle, Pedidos, Services, Ventas, Transferencias, Clientes, Motos, Controles, Trabajos, Reportes, Auditoría, Administración y Wiki sin errores de carga visibles. Las operaciones de escritura se ejercitaron contra la API con los registros QA autorizados.
- Verificaciones locales registradas durante la auditoría: `mvn test` — 75 pruebas aprobadas; `npm test` — 6 pruebas aprobadas; `npm run lint` — 0 errores y 3 advertencias existentes sobre uso de `<img>`. Las suites backend posteriores a las correcciones se detallan en la verificación final de este archivo.
- Se comparó el tablero comercial con la lista de Ventas: el tablero cuenta motos actualmente en la sección comercial y la lista conserva fichas de venta históricas. La diferencia observada (3 motos frente a 4 fichas) se explica por una ficha vendida cuya moto volvió al circuito de taller y tiene una ficha de taller posterior; no se confirma un bug de conteo.

## Hallazgos

### RESUELTO — P2 — PATCH de revisión borraba el texto de corrección omitido

**Reproducción**

Con una ficha en revisión cuyo control tenga `correccionNecesaria`, guardar una nota o cambiar el estado desde la pantalla envía `estado` y `observacion`, pero omite `correccionNecesaria`. El servicio asigna `blank(r.correccionNecesaria())` al campo guardado; como el valor omitido llega como `null`, borra la corrección existente. La misma pérdida ocurre con cualquier cliente API que envíe solo algunos campos del PATCH.

**Evidencia**

- `AviantoFront/components/views.tsx:579` construye el PATCH de nota sin `correccionNecesaria`.
- `AviantoBack/src/main/java/com/avianto/back/ApiDtos.java` ahora conserva si Jackson recibió cada propiedad de texto.
- `AviantoBack/src/main/java/com/avianto/back/ApiService.java` modifica solo los campos enviados.
- Reproducido en la aplicación con una ficha de prueba: se guardó una corrección, se envió el PATCH parcial de estado/observación omitiendo `correccionNecesaria` y la respuesta dejó ese campo en `null`. La nota QA se restauró después. También se confirmó en una prueba local aislada con repositorio simulado.
- Regresión automatizada `partialRevisionControlUpdatePreservesCorrectionWhenOmitted`: falló antes del cambio y pasó después; otra prueba confirma que `null` explícito borra solo el campo enviado.

**Impacto y siguiente paso**

La omisión ahora conserva el valor; `null` o vacío explícitos limpian solo la nota enviada. Verificado con las regresiones de `RevisionWorkflowTest`.

### RESUELTO — P2 — PATCH de configuración de service borraba observaciones omitidas

**Reproducción**

Con una moto que tenga `serviceObservaciones`, enviar `PATCH /api/motovehiculos/{id}/config-service` con `{"kmServicePeriodo":6000}` y omitir `serviceObservaciones`. El servicio cambia el intervalo y asigna `null` a la observación porque procesa ese campo aunque no venga en el cuerpo.

**Evidencia**

- `AviantoBack/src/main/java/com/avianto/back/ApiDtos.java` registra si llegó `serviceObservaciones`, incluyendo `null` explícito.
- `AviantoBack/src/main/java/com/avianto/back/ApiService.java` conserva el valor si la propiedad fue omitida.
- Reproducido en la aplicación con una moto de prueba: se guardó `serviceObservaciones`, se envió `PATCH /api/motovehiculos/{id}/config-service` con solo `{"kmServicePeriodo":6000}` y la respuesta dejó `serviceObservaciones` en `null`. La nota QA se restauró después. También se confirmó en una prueba local aislada con repositorio simulado.
- Regresión automatizada `partialMotoConfigPreservesServiceObservationsWhenOmitted`: falló antes del cambio y pasó después; otra prueba confirma el borrado con `null` explícito.

**Impacto y siguiente paso**

La omisión ahora conserva la nota; `null` o vacío explícitos siguen permitiendo borrarla, incluido el formulario actual de configuración. Verificado con las regresiones de `OperationalIntegrityTest`.

### RESUELTO — P2 — El proxy público servía el frontend en las rutas de salud y documentación API

**Reproducción observada**

1. Abrir `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` o `/actuator/info`: todos devuelven `200` con `Content-Type: text/html` y el HTML del frontend, en lugar de la respuesta JSON de Actuator.
2. Abrir `/v3/api-docs` o `/swagger-ui.html`: también devuelven `200` con el HTML del frontend, no el documento OpenAPI ni Swagger.
3. La ruta configurada por Springdoc es `/v3/api-docs`; `/api/v3/api-docs` es una ruta inexistente y el handler genérico la convertía en `500` en vez de `404`.

**Impacto**

Los monitores que comprueban las rutas públicas de readiness/liveness pueden tomar el `200` del frontend como señal de salud aunque no hayan comprobado el backend. La documentación Swagger/OpenAPI no está disponible para Administración desde el dominio publicado.

**Evidencia relacionada**

- `AviantoBack/documentacion.md` indica que en producción Caddy enruta `/api` al backend y el resto al frontend; Actuator y Springdoc están configurados en rutas raíz.
- `AviantoBack/ops/nginx.conf` y `AviantoBack/ops/avianto.caddy` ahora envían `/api`, `/actuator`, `/swagger-ui` y `/v3/api-docs` al backend.
- `AviantoBack/src/main/resources/application.yml` configura Actuator y Springdoc en sus rutas raíz.
- `AviantoBack/src/main/java/com/avianto/back/SecurityConfig.java` mantiene Swagger/OpenAPI restringido a Administración; `Errors` convierte rutas no registradas en `404`.

**Solución aplicada y verificación en producción**

El Caddyfile de `/opt/infra/edge/Caddyfile` se respaldó en `/opt/infra/edge/Caddyfile.bak.20261026-171302`; se actualizó solo el bloque de Avianto, pasó `caddy validate` y se reinició `edge-caddy` con autorización. La API desplegada desde `0cefc5f` y PostgreSQL están saludables; el esquema está en versión 29 y Flyway confirmó que no hacía falta migrar. Readiness y liveness responden `200` con JSON; `/v3/api-docs` devuelve `200` a Administración y `403` a Operario; `/api/v3/api-docs` y una ruta API desconocida devuelven `404` con Administración; `/app` conserva `200` HTML. `/swagger-ui.html` redirige con `302` a `/swagger-ui/index.html`; la ruta final responde `200` a Administración con Bearer y `403` a Operario. La prueba HTTP manual conserva explícitamente la autorización al seguir la redirección.

### PENDIENTE DE DECISIÓN — P2 — Swagger UI no envía el JWT de la sesión Avianto al cargar OpenAPI

**Reproducción**

1. Iniciar sesión como Administración en `/app`; `AviantoFront/lib/auth.ts` guarda la sesión en `sessionStorage` con la clave `avianto.session`.
2. La API sirve `/swagger-ui/index.html` a Administración y el endpoint `/v3/api-docs/swagger-config` devuelve `200` con un Bearer válido.
3. `swagger-initializer.js` no configura un `requestInterceptor` que envíe ese Bearer. La petición del navegador a `/v3/api-docs/swagger-config` llega sin autorización y devuelve `403`; OpenAPI continúa protegido correctamente para Operario y usuarios anónimos.

**Impacto**

Administración puede descargar OpenAPI usando Bearer, pero Swagger UI no puede cargar automáticamente la definición desde una sesión iniciada en Avianto. Exponer `/v3/api-docs` anónimamente permitiría leer el esquema a cualquier visitante.

**Decisión pendiente**

Se recomienda conservar OpenAPI restringido a Administración y hacer que Swagger UI adjunte el JWT de la sesión Avianto a sus solicitudes del mismo origen. La alternativa es exponer la documentación públicamente, lo que cambia la política de seguridad; no se aplicará sin indicación.

### RESUELTO — P3 — Las colecciones de propietarios y services aceptaban una moto inexistente

**Reproducción**

Con una sesión de Operario, consultar `GET /api/motovehiculos/00000000-0000-0000-0000-000000000000/propietarios` y `/services`: ambos responden `200 []`. La ruta hermana `/api/motovehiculos/{id}/transferencias` con el mismo UUID responde `404`. Con una moto existente, las tres rutas responden `200`.

**Criterio de contrato**

Una colección anidada bajo una moto inexistente debe responder `404`, igual que transferencias: no existe el recurso padre cuya colección se está consultando. Este criterio fue definido durante la auditoría para mantener una sola regla en los subrecursos.

**Solución aplicada**

`owners` y `services` validan el padre con `db.get`, igual que transferencias e historial. Pruebas unitarias y MockMvc comprueban el `404` uniforme para los tres subrecursos.

### RESUELTO — P3 — El contrato API del frontend contenía rutas de una versión anterior

**Reproducción**

`AviantoFront/docs/api-contract.md` documenta rutas como `/catalogo-items`, `/pedidos`, `/reportes/top-items`, `/configuracion/categorias-catalogo` y `/usuarios`, que no existen en los 113 mappings actuales de `ApiController`. La búsqueda de llamadas del frontend fuera de ese documento no encontró uso de esas rutas antiguas.

**Impacto y siguiente paso**

`AviantoFront/docs/api-contract.md` ahora enumera los 113 mappings vigentes, elimina referencias a catálogo/pedidos/rutas retirados y documenta nombres DTO, PATCH parcial, archivos y reglas `404`.

## Verificación y alcance restante

- Se ejercitó cada mapping al menos una vez y cada operación de negocio no GET tuvo un recorrido válido con Administración. Esto verifica contratos y flujos principales; no equivale a combinaciones exhaustivas de todos los valores, pruebas de carga, concurrencia ni pruebas de recuperación ante fallos de infraestructura.
- Las cinco correcciones se cubrieron con pruebas de regresión. El resto del alcance no incluyó pruebas de carga, concurrencia ni fallos de infraestructura.
- Verificación final local tras las correcciones: backend `mvn test` — 86 pruebas aprobadas; frontend `npm test` — build completado y 6 pruebas aprobadas; `npm run lint` — 0 errores y 3 advertencias existentes de `<img>`. `git diff --check` no encontró errores de espacios.
- Despliegue comprobado: `avianto-api`, `avianto-postgres` y `edge-caddy` están saludables; las sondas públicas devuelven JSON y el proxy conserva la ruta `/app` al frontend. La ruta interactiva de Swagger redirige correctamente, pero su carga automática del esquema queda pendiente de decisión y solución.
- Los registros de auditoría permanecen en el ambiente de pruebas según lo autorizado; sus nombres usan el prefijo `QA-AUDIT-20261026-A`.

<details>
<summary>Inventario de endpoints del controlador — 113 mappings</summary>

### GET (48)

`/api/auth/me`, `/api/clientes`, `/api/clientes/autocomplete`, `/api/clientes/{id}`, `/api/motovehiculos`, `/api/motovehiculos/autocomplete`, `/api/motovehiculos/{id}`, `/api/ventas`, `/api/ventas/{id}`, `/api/motovehiculos/{id}/venta`, `/api/perfiles`, `/api/perfiles/{id}`, `/api/motovehiculos/{id}/propietarios`, `/api/motovehiculos/{id}/transferencias`, `/api/transferencias`, `/api/motovehiculos/{id}/services`, `/api/motovehiculos/{id}/services/historial`, `/api/services/proximos`, `/api/fichas`, `/api/fichas/{id}`, `/api/fichas/{id}/pagos`, `/api/fichas/{id}/repuestos`, `/api/fichas/{id}/trabajos`, `/api/fichas/{id}/fotos/{fotoId}`, `/api/fichas/{id}/pdf`, `/api/fichas/{id}/revision`, `/api/repuestos`, `/api/repuestos/{id}`, `/api/repuestos/{id}/pagos`, `/api/auditoria`, `/api/reportes/resumen`, `/api/reportes/evolucion`, `/api/dashboard`, `/api/dashboard/taller`, `/api/dashboard/fichas`, `/api/dashboard/ventas`, `/api/configuracion/trabajos`, `/api/configuracion/trabajos/autocomplete`, `/api/configuracion/controles`, `/api/configuracion/ventas/checklist`, `/api/configuracion/marcas-moto`, `/api/configuracion/categorias`, `/api/configuracion/usuarios`, `/api/clientes/export.xlsx`, `/api/motovehiculos/export.xlsx`, `/api/fichas/export.xlsx`, `/api/repuestos/export.xlsx`, `/api/transferencias/export.xlsx`.

### POST (31)

`/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/clientes`, `/api/motovehiculos`, `/api/motovehiculos/{id}/ingreso`, `/api/motovehiculos/{id}/venta/completar`, `/api/ventas/{id}/checklist/sincronizar`, `/api/ventas/{id}/transferencia`, `/api/ventas/{id}/transferencia/cancelar`, `/api/ventas/{id}/transferencia/asistencia`, `/api/ventas/{id}/completar`, `/api/perfiles`, `/api/motovehiculos/{id}/services`, `/api/fichas`, `/api/fichas/{id}/entregar`, `/api/fichas/{id}/pagos`, `/api/fichas/{id}/pagos/{pagoId}/anular`, `/api/fichas/{id}/trabajos`, `/api/fichas/{id}/fotos`, `/api/fichas/{id}/revision/preparar`, `/api/fichas/{id}/revision/aprobar`, `/api/repuestos`, `/api/repuestos/{id}/pagos`, `/api/repuestos/{id}/pagos/{pagoId}/anular`, `/api/configuracion/trabajos`, `/api/configuracion/controles`, `/api/configuracion/ventas/checklist`, `/api/configuracion/marcas-moto`, `/api/configuracion/categorias`, `/api/configuracion/usuarios`.

### PUT (14)

`/api/clientes/{id}`, `/api/motovehiculos/{id}`, `/api/ventas/{id}/comprador`, `/api/ventas/{id}/transferencia/cita`, `/api/fichas/{id}`, `/api/fichas/{id}/trabajos/{trabajoId}`, `/api/repuestos/{id}`, `/api/repuestos/{id}/items/{itemId}`, `/api/configuracion/trabajos/{id}`, `/api/configuracion/controles/{id}`, `/api/configuracion/ventas/checklist/{id}`, `/api/configuracion/marcas-moto/{id}`, `/api/configuracion/categorias/{id}`, `/api/configuracion/usuarios/{id}`.

### PATCH (8)

`/api/motovehiculos/{id}/circuito`, `/api/motovehiculos/{id}/config-service`, `/api/ventas/{id}/items/{itemId}`, `/api/fichas/{id}/estado`, `/api/fichas/{id}/trabajos/{trabajoId}/estado`, `/api/fichas/{id}/revision/controles/{controlId}`, `/api/repuestos/{id}/estado`, `/api/repuestos/{id}/items/{itemId}/estado`.

### DELETE (12)

`/api/clientes/{id}`, `/api/motovehiculos/{id}`, `/api/fichas/{id}`, `/api/fichas/{id}/trabajos/{trabajoId}`, `/api/repuestos/{id}`, `/api/repuestos/{id}/items/{itemId}`, `/api/configuracion/trabajos/{id}`, `/api/configuracion/controles/{id}`, `/api/configuracion/ventas/checklist/{id}`, `/api/configuracion/marcas-moto/{id}`, `/api/configuracion/categorias/{id}`, `/api/configuracion/usuarios/{id}`.

</details>
