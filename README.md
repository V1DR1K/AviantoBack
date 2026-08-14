# AviantoBack

Standalone Java 21 / Spring Boot 3 backend for Avianto. The API base path is `/api`; Swagger is available at `/swagger-ui.html` and health at `/actuator/health`.

## Run locally

1. Copy `.env.example` to `.env` and replace every secret/password. `ADMIN_*` and `OPERARIO_*` are required only for the first startup, after Flyway has created the schema.
2. Start the stack with `docker compose --env-file .env up --build`.
3. The application is bound to `127.0.0.1:8081`; PostgreSQL has no host port.

For a locally installed PostgreSQL, export the variables from `.env` and run `./mvnw spring-boot:run`. The supplied wrapper downloads Maven 3.9.9 into `.mvn/wrapper/dists` on first use. Java 21 and either `curl` or `wget` are prerequisites.

## Design notes

- All persisted public IDs are UUIDs. Dates use `Instant` (UTC); reporting groups dates in `America/Argentina/Buenos_Aires`.
- Flyway owns the forward-only schema and initial brand/category data. Users are created by `SeedService` after Flyway using BCrypt hashes from environment variables, never from SQL migrations.
- Login accepts the user email in `username`. Access JWTs are short-lived; opaque refresh tokens are SHA-256 hashed and persisted, rotated on refresh, and revoked on logout.
- Deletes are logical (`deleted_at`, `deleted_by`). Configuration records and users enforce their dependency/last-admin rules. Orders preserve their line snapshots and validate motorcycle ownership and state transitions.
- Exports accept the same filters plus `columns`; PDF and XLSX are generated server-side. Photos are stored as PostgreSQL bytes for the MVP and limited to 5 MB.
- Entering a motorcycle in `Venta` is atomic: it is immediately available as `En venta`; no intermediate sales state exists.
- Workshop flow: `Ingresada Taller` -> `Pendiente` -> `En proceso` -> `En revisión` -> `Terminada` -> `Entregada`. Approving the review marks the job as `Terminada`; delivery is recorded separately.
- `Disponible` means the motorcycle is registered but outside both operational circuits (`ingresada=false` and no active section). It is not synonymous with `En venta`: only `En venta` represents a motorcycle entered into the sales circuit. Both states are mutually exclusive.
- A ficha summary and its PDF include only `RepuestoPedido` records linked directly to that ficha. Their totals are shown separately from the ficha total and combined as the client-facing budget total without changing either persisted operation.

## Tests

Run `./mvnw test`. The project includes a focused domain test; integration tests can use a Testcontainers PostgreSQL instance in CI when Docker is available.
