# AviantoBack

Standalone Java 21 / Spring Boot 3 backend for Avianto. The API base path is `/api`; Swagger is available at `/swagger-ui.html` and health at `/actuator/health`.

## Run locally

1. Copy `.env.example` to `.env` and replace every secret/password. `ADMIN_*` and `OPERARIO_*` are required only for the first startup, after Flyway has created the schema.
2. For the complete local stack, keep `AviantoBack` and `AviantoFront` as sibling directories and run `docker compose --env-file .env -f docker-compose.full.yml up --build`.
3. Open `http://127.0.0.1:8080`; the proxy is the only published application port and PostgreSQL remains private.

For a locally installed PostgreSQL, export the variables from `.env` and run `./mvnw spring-boot:run`. The supplied wrapper downloads Maven 3.9.9 into `.mvn/wrapper/dists` on first use. Java 21 and either `curl` or `wget` are prerequisites.

## Design notes

- All persisted public IDs are UUIDs. Dates use `Instant` (UTC); reporting groups dates in `America/Argentina/Buenos_Aires`.
- Flyway owns the forward-only schema and initial brand/category data. Users are created by `SeedService` after Flyway using BCrypt hashes from environment variables, never from SQL migrations.
- Login accepts the user email in `username`. Access JWTs are short-lived; opaque refresh tokens are SHA-256 hashed and persisted, rotated on refresh, and revoked on logout.
- Deletes are logical (`deleted_at`, `deleted_by`). Configuration records and users enforce their dependency/last-admin rules. Orders preserve their line snapshots and validate motorcycle ownership and state transitions.
- Exports accept the same filters plus `columns`; PDF and XLSX are generated server-side. Photos are stored as PostgreSQL bytes for the MVP and limited to 5 MB.
- Entering a motorcycle in `Venta` is atomic: it is immediately available as `En venta`; no intermediate sales state exists.
- Workshop flow: `Ingresada Taller` -> `Pendiente` -> `En proceso` -> `En revisión` -> `Terminada` -> `Entregada`. Approving the review marks the job as `Terminada`; delivery is recorded separately.
- Completing or cancelling every work does not advance a ficha automatically. The operator must explicitly send it from `En proceso` to `En revisión` after reviewing the work order.
- Operator permissions are controlled by `AVIANTO_OPERATOR_PERMISSIONS`, a comma-separated list such as `FICHA_WRITE,REVIEW_WRITE,SALE_CHECKLIST,MOTO_CIRCUIT,SERVICE_WRITE`.
- `Disponible` means the motorcycle is registered but outside both operational circuits (`ingresada=false` and no active section). It is not synonymous with `En venta`: only `En venta` represents a motorcycle entered into the sales circuit. Both states are mutually exclusive.
- A ficha summary and its PDF include only `RepuestoPedido` records linked directly to that ficha. Their totals are shown separately from the ficha total and combined as the client-facing budget total without changing either persisted operation.

## Tests

Run `./mvnw test`. The complete local stack can be validated with `docker compose --env-file .env -f docker-compose.full.yml config` before startup.

Backups are created atomically with mode `0600`. Restore requires an explicit confirmation: `AVIANTO_ALLOW_RESTORE=1 ./restore.sh /ruta/al/respaldo.sql.gz`; stop the application or isolate the database before restoring.
