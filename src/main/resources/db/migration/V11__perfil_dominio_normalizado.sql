-- Un Perfil es único por dominio, independientemente de espacios o guiones.
CREATE UNIQUE INDEX ux_moto_patente_normalizada_active
  ON motovehiculo (upper(regexp_replace(patente, '[^A-Za-z0-9]', '', 'g')))
  WHERE deleted_at IS NULL;
