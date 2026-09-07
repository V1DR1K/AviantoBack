ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS entidad VARCHAR(120);
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS entidad_id UUID;
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS antes_json TEXT;
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS despues_json TEXT;
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS motivo TEXT;
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(80);

CREATE INDEX IF NOT EXISTS ix_auditoria_entidad_id ON auditoria (entidad, entidad_id);
CREATE INDEX IF NOT EXISTS ix_auditoria_correlation_id ON auditoria (correlation_id);
