ALTER TABLE pago ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pago_idempotency_key ON pago (idempotency_key) WHERE idempotency_key IS NOT NULL;
