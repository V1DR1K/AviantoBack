package com.avianto.back;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Administración de copias de seguridad. Todo exige rol Administración, salvo el retorno de Google. */
@RestController @RequestMapping("/api/configuracion/backups")
class BackupController {
  record SettingsRequest(boolean destinoLocal, boolean destinoDrive, boolean autoEnabled, @Min(0) @Max(23) int autoHour, @Size(max = 60) String subcarpeta, @Min(1) @Max(365) int retentionDays) {}
  record DriveCredentialsRequest(@Size(max = 300) String clientId, @Size(max = 300) String clientSecret) {}
  record LocalInfo(String hostDir, String containerDir, String effectiveDir, boolean writable) {}
  record DriveInfo(boolean hasCredentials, String clientId, boolean connected, String account, String folderName, String redirectUri) {}
  record StatusResponse(boolean running, boolean destinoLocal, boolean destinoDrive, boolean autoEnabled, int autoHour, String subcarpeta, int retentionDays, LocalInfo local, DriveInfo drive, List<BackupService.Run> history) {}

  private final BackupService backups;
  BackupController(BackupService backups) { this.backups = backups; }

  private static UUID actor() { try { return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName()); } catch (Exception e) { return null; } }

  @GetMapping @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse status() {
    BackupService.Settings s = backups.settings();
    boolean hasCredentials = s.driveClientId() != null && backups.decrypt(s.driveClientSecretEnc()) != null;
    String effective = (backups.hostDir().isBlank() ? backups.baseDir().toString() : backups.hostDir()) + (s.subcarpeta().isBlank() ? "" : "/" + s.subcarpeta());
    return new StatusResponse(backups.running(), s.destinoLocal(), s.destinoDrive(), s.autoEnabled(), s.autoHour(), s.subcarpeta(), s.retentionDays(),
      new LocalInfo(backups.hostDir(), backups.baseDir().toString(), effective, backups.localWritable(s)),
      new DriveInfo(hasCredentials, s.driveClientId(), backups.driveConnected(s), s.driveAccount(), s.driveFolderName(), backups.redirectUri()), backups.history(10));
  }

  @PutMapping @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse save(@Valid @RequestBody SettingsRequest r) { backups.saveSettings(r.destinoLocal(), r.destinoDrive(), r.autoEnabled(), r.autoHour(), r.subcarpeta(), r.retentionDays()); return status(); }

  @PostMapping("/run") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  Map<String, Object> run() { backups.startManual(actor()); return Map.of("accepted", true); }

  @PutMapping("/drive/credentials") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse credentials(@Valid @RequestBody DriveCredentialsRequest r) { backups.saveDriveCredentials(r.clientId(), r.clientSecret()); return status(); }

  @PostMapping("/drive/connect") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  Map<String, String> connect() { return Map.of("url", backups.startDriveLink(actor())); }

  @PostMapping("/drive/disconnect") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse disconnect() { backups.disconnectDrive(actor()); return status(); }

  /** Retorno del navegador desde Google. No lleva Bearer: lo protege el `state` de un solo uso. */
  @GetMapping("/drive/callback")
  ResponseEntity<Void> callback(@RequestParam(required = false) String state, @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
    String problem = backups.finishDriveLink(state, code, error);
    String target = backups.publicUrl() + "/administracion?drive=" + (problem == null ? "ok" : "error&detalle=" + URLEncoder.encode(problem, StandardCharsets.UTF_8));
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(target).toString()).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }
}
