-- Google Drive deja de usar OAuth y Google Cloud: las copias se suben con un script de
-- Google Apps Script publicado desde la cuenta del taller. El flujo OAuth anterior nunca
-- llegó a vincularse en producción, así que sus columnas se eliminan sin pérdida de datos.
-- Idempotente como V32: un backup restaurado puede traer un historial de Flyway previo.
ALTER TABLE backup_settings
  DROP COLUMN IF EXISTS drive_client_id,
  DROP COLUMN IF EXISTS drive_client_secret_enc,
  DROP COLUMN IF EXISTS drive_refresh_token_enc,
  DROP COLUMN IF EXISTS drive_folder_id,
  ADD COLUMN IF NOT EXISTS drive_script_url_enc TEXT,
  ADD COLUMN IF NOT EXISTS drive_script_token_enc TEXT;
