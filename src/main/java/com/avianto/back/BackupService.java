package com.avianto.back;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Copias de seguridad de la base (pg_dump) a una carpeta de esta PC y/o a Google Drive,
 * manuales o automáticas una vez por día. Solo corre una copia a la vez.
 */
@Service
class BackupService {
  private static final Logger log = LoggerFactory.getLogger(BackupService.class);
  private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
  private static final Pattern SUBFOLDER = Pattern.compile("^[\\p{L}\\p{N} _-][\\p{L}\\p{N} _.-]{0,59}$");
  private static final Pattern JDBC = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+).*");
  private static final String DUMP_END_MARK = "PostgreSQL database dump complete";
  private static final int KEEP_NEWEST = 3;
  static final String SCRIPT_RESOURCE = "drive/avianto-backup.gs";
  static final String TOKEN_PLACEHOLDER = "__AVIANTO_TOKEN__";
  static final String SCRIPT_URL_HINT = "La dirección tiene que ser la de la aplicación web de Google Apps Script: empieza con https://script.google.com/macros/s/ y termina en /exec.";

  record Settings(boolean destinoLocal, boolean destinoDrive, boolean autoEnabled, int autoHour, String subcarpeta, int retentionDays, String driveScriptUrlEnc, String driveScriptTokenEnc, String driveAccount, String driveFolderName, LocalDate lastAutoOkDate, Instant lastAutoAttemptAt) {}
  record Run(String id, Instant startedAt, Instant finishedAt, String trigger, String status, String fileName, Long sizeBytes, String localResult, String driveResult, String message) {}

  private final JdbcTemplate jdbc; private final DriveScriptClient drive;
  private final Path baseDir; private final String hostDir, pgDump, dbHost, dbPort, dbName, dbUser, dbPassword; private final byte[] secretKey;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "avianto-backup"); t.setDaemon(true); return t; });
  private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(false);
  private final SecureRandom random = new SecureRandom();

  BackupService(JdbcTemplate jdbc, DriveScriptClient drive,
      @Value("${app.backup.dir:./backups}") String dir, @Value("${app.backup.host-dir:}") String hostDir, @Value("${app.backup.pg-dump:pg_dump}") String pgDump,
      @Value("${spring.datasource.url}") String url, @Value("${spring.datasource.username}") String user, @Value("${spring.datasource.password:}") String password,
      @Value("${app.jwt.secret}") String jwtSecret) throws Exception {
    this.jdbc = jdbc; this.drive = drive; this.baseDir = Paths.get(dir).toAbsolutePath().normalize(); this.hostDir = hostDir; this.pgDump = pgDump;
    Matcher m = JDBC.matcher(url);
    this.dbHost = m.matches() ? m.group(1) : "localhost"; this.dbPort = m.matches() && m.group(2) != null ? m.group(2) : "5432"; this.dbName = m.matches() ? m.group(3) : "avianto";
    this.dbUser = user; this.dbPassword = password;
    this.secretKey = MessageDigest.getInstance("SHA-256").digest((jwtSecret + "|avianto-backup").getBytes(StandardCharsets.UTF_8));
  }

  @PostConstruct void recoverInterrupted() {
    try { jdbc.update("update backup_run set status='ERROR', finished_at=?, message='La copia se interrumpió porque el sistema se reinició.' where status='RUNNING'", Timestamp.from(Instant.now())); }
    catch (Exception e) { log.warn("No se pudo revisar el historial de backups: {}", e.getMessage()); }
  }
  @PreDestroy void shutdown() { executor.shutdownNow(); }

  // ---------- configuración ----------

  private void ensureSettingsRow() {
    jdbc.update("insert into backup_settings (id,destino_local,destino_drive,auto_enabled,auto_hour,subcarpeta,retention_days) select 1,true,false,false,3,'',14 where not exists (select 1 from backup_settings where id=1)");
  }

  Settings settings() {
    ensureSettingsRow();
    return jdbc.queryForObject("select * from backup_settings where id=1", (rs, i) -> new Settings(rs.getBoolean("destino_local"), rs.getBoolean("destino_drive"), rs.getBoolean("auto_enabled"), rs.getInt("auto_hour"), rs.getString("subcarpeta"), rs.getInt("retention_days"),
      rs.getString("drive_script_url_enc"), rs.getString("drive_script_token_enc"), rs.getString("drive_account"), rs.getString("drive_folder_name"),
      rs.getDate("last_auto_ok_date") == null ? null : rs.getDate("last_auto_ok_date").toLocalDate(), rs.getTimestamp("last_auto_attempt_at") == null ? null : rs.getTimestamp("last_auto_attempt_at").toInstant()));
  }

  boolean driveConnected(Settings s) { return decrypt(s.driveScriptUrlEnc()) != null && decrypt(s.driveScriptTokenEnc()) != null; }

  void saveSettings(boolean local, boolean driveOn, boolean auto, int hour, String subfolder, int retention) {
    Settings s = settings();
    String sub = subfolder == null ? "" : subfolder.trim();
    if (!local && !driveOn) throw new BusinessException(422, "Elegí al menos un lugar donde guardar las copias: esta PC, Google Drive o ambos.");
    if (!sub.isEmpty() && !SUBFOLDER.matcher(sub).matches()) throw new BusinessException(422, "El nombre de la subcarpeta solo puede tener letras, números, espacios, guiones, guiones bajos y puntos (sin barras).");
    if (hour < 0 || hour > 23) throw new BusinessException(422, "La hora debe estar entre 0 y 23.");
    if (retention < 1 || retention > 365) throw new BusinessException(422, "Los días de conservación deben estar entre 1 y 365.");
    if (driveOn && !driveConnected(s)) throw new BusinessException(422, "Para guardar en Google Drive primero vinculá tu cuenta (paso 2).");
    localDir(sub);
    jdbc.update("update backup_settings set destino_local=?, destino_drive=?, auto_enabled=?, auto_hour=?, subcarpeta=?, retention_days=? where id=1", local, driveOn, auto, hour, sub, retention);
    audit(null, "BACKUP_CONFIG", "Configuración de backups: " + (local ? "PC" : "") + (local && driveOn ? " + " : "") + (driveOn ? "Google Drive" : "") + ", automático " + (auto ? "todos los días a las " + hour + ":00" : "desactivado") + ", conservar " + retention + " días.");
  }

  /** Carpeta final dentro de la carpeta montada. Rechaza cualquier intento de salirse de ella. */
  Path localDir(String subfolder) {
    Path dir = subfolder == null || subfolder.isBlank() ? baseDir : baseDir.resolve(subfolder.trim()).normalize();
    if (!dir.startsWith(baseDir)) throw new BusinessException(422, "La subcarpeta no es válida.");
    return dir;
  }
  Path baseDir() { return baseDir; }
  String hostDir() { return hostDir; }

  boolean localWritable(Settings s) {
    try { Path dir = localDir(s.subcarpeta()); Files.createDirectories(dir); Path probe = Files.createTempFile(dir, ".probe", ".tmp"); Files.deleteIfExists(probe); return true; }
    catch (Exception e) { return false; }
  }

  // ---------- Google Drive mediante Google Apps Script (sin OAuth ni Google Cloud) ----------

  /** Código del script con la clave de esta instalación. La clave se crea la primera vez que se pide. */
  String driveScript() {
    Settings s = settings();
    String token = decrypt(s.driveScriptTokenEnc());
    if (token == null) {
      // Una clave nueva invalida cualquier script anterior: se desvincula para no quedar con datos viejos.
      token = HexFormat.of().formatHex(randomBytes(32));
      jdbc.update("update backup_settings set drive_script_token_enc=?, drive_script_url_enc=null, drive_account=null, drive_folder_name=null, destino_drive=false, destino_local=true where id=1", encrypt(token));
    }
    return renderScript(template(), token);
  }

  static String renderScript(String template, String token) { return template.replace(TOKEN_PLACEHOLDER, token); }

  private static String template() {
    try (InputStream in = new ClassPathResource(SCRIPT_RESOURCE).getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    catch (IOException e) { throw new IllegalStateException("Falta el script de copias en la aplicación.", e); }
  }

  /** Solo se acepta la aplicación web de Apps Script: el backend le manda datos a esa dirección. */
  static String validScriptUrl(String raw) {
    String url = raw == null ? "" : raw.trim();
    try {
      URI uri = new URI(url);
      String path = uri.getPath() == null ? "" : uri.getPath();
      if ("https".equalsIgnoreCase(uri.getScheme()) && "script.google.com".equalsIgnoreCase(uri.getHost()) && path.startsWith("/macros/s/") && path.endsWith("/exec")) return url;
    } catch (URISyntaxException ignored) { }
    throw new BusinessException(422, SCRIPT_URL_HINT);
  }

  void linkDrive(UUID userId, String rawUrl) {
    String url = validScriptUrl(rawUrl);
    String token = decrypt(settings().driveScriptTokenEnc());
    if (token == null) throw new BusinessException(422, "Primero copiá el script (paso 2) y pegalo en Google.");
    DriveScriptClient.Result result;
    try { result = drive.ping(url, token); }
    catch (DriveAuthException e) { throw new BusinessException(422, e.getMessage()); }
    catch (IOException e) { throw new BusinessException(422, "No se pudo conectar con el script: " + clean(e)); }
    jdbc.update("update backup_settings set drive_script_url_enc=?, drive_account=?, drive_folder_name=? where id=1", encrypt(url), result.account(), result.folderName());
    audit(userId, "BACKUP_DRIVE_VINCULAR", "Se vinculó Google Drive (" + result.account() + ").");
  }

  void disconnectDrive(UUID userId) {
    jdbc.update("update backup_settings set drive_script_url_enc=null, drive_script_token_enc=null, drive_account=null, drive_folder_name=null, destino_drive=false, destino_local=true where id=1");
    audit(userId, "BACKUP_DRIVE_DESVINCULAR", "Se desvinculó Google Drive.");
  }

  // ---------- ejecución ----------

  boolean running() { return running.get(); }

  void startManual(UUID userId) {
    Settings s = settings();
    if (!s.destinoLocal() && !s.destinoDrive()) throw new BusinessException(422, "Elegí dónde guardar la copia antes de generarla.");
    if (!running.compareAndSet(false, true)) throw new BusinessException(409, "Ya hay una copia en curso. Esperá a que termine.");
    launch("MANUAL", userId);
  }

  @Scheduled(cron = "0 * * * * *", zone = "America/Argentina/Buenos_Aires")
  void automaticTick() {
    try {
      if (running.get()) return;
      Settings s = settings();
      if (!s.autoEnabled() || (!s.destinoLocal() && !s.destinoDrive())) return;
      ZonedDateTime now = ZonedDateTime.now(ZONE);
      if (now.toLocalDate().equals(s.lastAutoOkDate()) || now.getHour() < s.autoHour()) return;
      if (s.lastAutoAttemptAt() != null && Duration.between(s.lastAutoAttemptAt(), now.toInstant()).toMinutes() < 60) return;
      if (!running.compareAndSet(false, true)) return;
      jdbc.update("update backup_settings set last_auto_attempt_at=? where id=1", Timestamp.from(Instant.now()));
      launch("AUTO", null);
    } catch (Exception e) { log.warn("No se pudo evaluar el backup automático: {}", e.getMessage()); }
  }

  private void launch(String trigger, UUID userId) {
    UUID runId = UUID.randomUUID();
    try {
      jdbc.update("insert into backup_run (id,started_at,trigger_type,status) values (?,?,?,'RUNNING')", runId, Timestamp.from(Instant.now()), trigger);
      executor.submit(() -> execute(runId, trigger, userId));
    } catch (RuntimeException e) { running.set(false); throw e; }
  }

  private void execute(UUID runId, String trigger, UUID userId) {
    Settings s = settings();
    String name = "avianto-" + DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()) + ".sql.gz";
    String localResult = null, driveResult = null, message = null; Long size = null; boolean localOk = false, driveOk = false; Path scratch = null;
    try {
      Path dumpFile;
      if (s.destinoLocal()) {
        try { dumpFile = dump(localDir(s.subcarpeta()), name); localOk = true; size = Files.size(dumpFile); localResult = "Guardada en " + (hostDir.isBlank() ? baseDir : hostDir) + (s.subcarpeta().isBlank() ? "" : "/" + s.subcarpeta()); pruneLocal(localDir(s.subcarpeta()), s.retentionDays()); }
        catch (Exception e) { localResult = "No se pudo guardar en esta PC: " + clean(e); dumpFile = null; }
      } else dumpFile = null;
      if (s.destinoDrive()) {
        try {
          if (dumpFile == null) { scratch = Files.createTempDirectory("avianto-backup"); dumpFile = dump(scratch, name); size = Files.size(dumpFile); }
          driveResult = uploadToDrive(s, dumpFile, name); driveOk = true;
        } catch (Exception e) { driveResult = "No se pudo subir a Google Drive: " + clean(e); }
      }
      String status = (!s.destinoLocal() || localOk) && (!s.destinoDrive() || driveOk) ? "OK" : (localOk || driveOk) ? "PARCIAL" : "ERROR";
      if (status.equals("ERROR")) message = "No se pudo generar la copia. Revisá el detalle de cada destino.";
      else if (status.equals("PARCIAL")) message = "La copia se guardó solo en uno de los destinos elegidos.";
      jdbc.update("update backup_run set finished_at=?, status=?, file_name=?, size_bytes=?, local_result=?, drive_result=?, message=? where id=?", Timestamp.from(Instant.now()), status, localOk || driveOk ? name : null, size, localResult, driveResult, message, runId);
      if ("AUTO".equals(trigger) && !status.equals("ERROR")) jdbc.update("update backup_settings set last_auto_ok_date=? where id=1", java.sql.Date.valueOf(LocalDate.now(ZONE)));
      audit(userId, "BACKUP_" + status, (trigger.equals("AUTO") ? "Backup automático" : "Backup manual") + " " + status.toLowerCase() + (localOk || driveOk ? ": " + name : "") + (localResult != null && !localOk ? " · " + localResult : "") + (driveResult != null && !driveOk ? " · " + driveResult : ""));
    } catch (Exception e) {
      log.error("Falló el backup", e);
      try { jdbc.update("update backup_run set finished_at=?, status='ERROR', message=? where id=?", Timestamp.from(Instant.now()), "Error inesperado: " + clean(e), runId); } catch (Exception ignored) { }
    } finally {
      if (scratch != null) deleteQuietly(scratch);
      running.set(false);
    }
  }

  private String uploadToDrive(Settings s, Path file, String name) throws IOException {
    String url = decrypt(s.driveScriptUrlEnc()), token = decrypt(s.driveScriptTokenEnc());
    if (url == null || token == null) throw new DriveAuthException("Google Drive no está vinculado. Volvé a vincularlo desde el paso 2.");
    DriveScriptClient.Result result = drive.upload(url, token, name, file, s.retentionDays(), KEEP_NEWEST);
    return "Subida a Google Drive (carpeta " + result.folderName() + ", cuenta " + result.account() + ")";
  }

  /** pg_dump → gzip → archivo temporal → verificación → renombrado atómico. Igual criterio que backup.sh. */
  Path dump(Path dir, String name) throws IOException, InterruptedException {
    Files.createDirectories(dir);
    Path tmp = dir.resolve("." + name + ".tmp"), stderr = Files.createTempFile("pg_dump", ".err"), fin = dir.resolve(name);
    try {
      ProcessBuilder pb = new ProcessBuilder(pgDump, "-h", dbHost, "-p", dbPort, "-U", dbUser, "-d", dbName, "--no-owner", "--no-privileges", "--clean", "--if-exists", "--exclude-table=backup_settings", "--exclude-table=backup_run");
      pb.environment().put("PGPASSWORD", dbPassword); pb.redirectError(stderr.toFile());
      Process process = pb.start();
      try (InputStream in = process.getInputStream(); OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp), 1 << 16)) { in.transferTo(out); }
      if (!process.waitFor(2, TimeUnit.HOURS)) { process.destroyForcibly(); throw new IOException("pg_dump tardó demasiado y se canceló."); }
      if (process.exitValue() != 0) throw new IOException("pg_dump falló: " + new String(Files.readAllBytes(stderr), StandardCharsets.UTF_8).trim());
      if (!endsWithDumpMark(tmp)) throw new IOException("El archivo generado está incompleto.");
      try { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")); } catch (UnsupportedOperationException | IOException ignored) { }
      Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return fin;
    } finally { Files.deleteIfExists(tmp); Files.deleteIfExists(stderr); }
  }

  private boolean endsWithDumpMark(Path gz) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(gz)), StandardCharsets.UTF_8))) {
      // Las versiones recientes de pg_dump agregan líneas "unrestrict" después de la marca final.
      Deque<String> tail = new ArrayDeque<>(); String line;
      while ((line = reader.readLine()) != null) if (!line.isBlank()) { tail.addLast(line); if (tail.size() > 5) tail.removeFirst(); }
      return tail.stream().anyMatch(l -> l.contains(DUMP_END_MARK));
    }
  }

  private void pruneLocal(Path dir, int retentionDays) throws IOException {
    Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> backups = files.filter(f -> f.getFileName().toString().matches("avianto-\\d{8}T\\d{6}Z\\.sql\\.gz")).sorted(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed()).toList();
      for (int i = KEEP_NEWEST; i < backups.size(); i++) if (Files.getLastModifiedTime(backups.get(i)).toInstant().isBefore(cutoff)) Files.deleteIfExists(backups.get(i));
    }
  }

  // ---------- historial ----------

  List<Run> history(int limit) {
    return jdbc.query("select * from backup_run order by started_at desc limit ?", (rs, i) -> new Run(rs.getString("id"), rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(), rs.getString("trigger_type"), rs.getString("status"), rs.getString("file_name"),
      rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes"), rs.getString("local_result"), rs.getString("drive_result"), rs.getString("message")), limit);
  }

  private void audit(UUID userId, String action, String text) {
    try { jdbc.update("insert into auditoria (id,fecha,usuario_id,modulo,accion,descripcion) values (?,?,?,?,?,?)", UUID.randomUUID(), Timestamp.from(Instant.now()), userId, "CONFIG", action, text); }
    catch (Exception e) { log.warn("No se pudo registrar la auditoría del backup: {}", e.getMessage()); }
  }

  // ---------- utilidades ----------

  private static String clean(Exception e) { String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); return m.length() > 350 ? m.substring(0, 350) : m; }
  private static void deleteQuietly(Path dir) { try (Stream<Path> s = Files.walk(dir)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { } }
  private byte[] randomBytes(int length) { byte[] bytes = new byte[length]; random.nextBytes(bytes); return bytes; }

  String encrypt(String plain) {
    try {
      byte[] iv = new byte[12]; random.nextBytes(iv); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8)); byte[] all = new byte[iv.length + ct.length]; System.arraycopy(iv, 0, all, 0, iv.length); System.arraycopy(ct, 0, all, iv.length, ct.length);
      return Base64.getEncoder().encodeToString(all);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  /** null si no hay dato o si la clave cambió (se pide volver a vincular). */
  String decrypt(String encoded) {
    if (encoded == null) return null;
    try {
      byte[] all = Base64.getDecoder().decode(encoded); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(128, all, 0, 12));
      return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    } catch (Exception e) { return null; }
  }
}
