-- Configuración y registro de las copias de seguridad administradas desde la app.
-- Estas tablas se excluyen de cada backup (ver BackupService): la vinculación con
-- Google Drive pertenece a la instalación, no a los datos del taller. Todo es
-- idempotente porque un backup restaurado puede traer un historial de Flyway previo.
CREATE TABLE IF NOT EXISTS backup_settings (
  id INTEGER PRIMARY KEY,
  destino_local BOOLEAN NOT NULL,
  destino_drive BOOLEAN NOT NULL,
  auto_enabled BOOLEAN NOT NULL,
  auto_hour INTEGER NOT NULL,
  subcarpeta VARCHAR(60) NOT NULL,
  retention_days INTEGER NOT NULL,
  drive_client_id VARCHAR(300),
  drive_client_secret_enc TEXT,
  drive_refresh_token_enc TEXT,
  drive_account VARCHAR(200),
  drive_folder_id VARCHAR(200),
  drive_folder_name VARCHAR(120),
  last_auto_ok_date DATE,
  last_auto_attempt_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ck_backup_hour CHECK (auto_hour BETWEEN 0 AND 23),
  CONSTRAINT ck_backup_retention CHECK (retention_days BETWEEN 1 AND 365)
);
INSERT INTO backup_settings (id, destino_local, destino_drive, auto_enabled, auto_hour, subcarpeta, retention_days)
SELECT 1, true, false, false, 3, '', 14 WHERE NOT EXISTS (SELECT 1 FROM backup_settings WHERE id = 1);

CREATE TABLE IF NOT EXISTS backup_run (
  id UUID PRIMARY KEY,
  started_at TIMESTAMP WITH TIME ZONE NOT NULL,
  finished_at TIMESTAMP WITH TIME ZONE,
  trigger_type VARCHAR(10) NOT NULL,
  status VARCHAR(10) NOT NULL,
  file_name VARCHAR(120),
  size_bytes BIGINT,
  local_result VARCHAR(400),
  drive_result VARCHAR(400),
  message TEXT
);
CREATE INDEX IF NOT EXISTS ix_backup_run_started_at ON backup_run (started_at DESC);
