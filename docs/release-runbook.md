# Release Runbook

## Pre-release

1. Run `docker compose --env-file .env -f docker-compose.full.yml config`.
2. Run `./ops/validate-migrations.sh` against a fresh project and volume.
3. Run the backend tests in a Maven container and `npm test` in `AviantoFront`.
4. Create a backup with `./backup.sh` while the current stack is healthy.
5. Validate a copy with `./ops/verify-restore.sh /path/to/avianto-*.sql.gz`.
6. Run `node ops/api-smoke.mjs` with credentials supplied only through the environment.

## Restore

Stop the application before restoring. `restore.sh` requires
`AVIANTO_ALLOW_RESTORE=1`, uses `ON_ERROR_STOP=1` and a single transaction, and
must be followed by a Flyway validation and health check.

## Rollback

Keep the previous backend and frontend image digests available. Roll back by
re-pointing the compose release to those immutable digests, recreating only
the application services, and checking `/actuator/health/readiness` plus the
frontend root. Never roll back database migrations independently; restore the
matching database backup when a schema rollback is required.

## V9

`V9__modelo_moto.sql` is destructive and may only run on a fresh database or
after an approved data migration procedure. It must not be applied to a real
production database as an ad-hoc rollback mechanism.
