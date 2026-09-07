ALTER TABLE ficha_foto ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ficha_foto_idempotency_key ON ficha_foto (idempotency_key) WHERE idempotency_key IS NOT NULL;
