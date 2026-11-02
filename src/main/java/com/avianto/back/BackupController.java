package com.avianto.back;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Administración de copias de seguridad. Todo exige rol Administración. */
@RestController @RequestMapping("/api/configuracion/backups")
class BackupController {
  record SettingsRequest(boolean destinoLocal, boolean destinoDrive, boolean autoEnabled, @Min(0) @Max(23) int autoHour, @Size(max = 60) String subcarpeta, @Min(1) @Max(365) int retentionDays) {}
  record LinkRequest(@NotBlank @Size(max = 500) String scriptUrl) {}
  record LocalInfo(String hostDir, String containerDir, String effectiveDir, boolean writable) {}
  record DriveInfo(boolean connected, String account, String folderName) {}
  record StatusResponse(boolean running, boolean destinoLocal, boolean destinoDrive, boolean autoEnabled, int autoHour, String subcarpeta, int retentionDays, LocalInfo local, DriveInfo drive, List<BackupService.Run> history) {}

  private final BackupService backups;
  BackupController(BackupService backups) { this.backups = backups; }

  private static UUID actor() { try { return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName()); } catch (Exception e) { return null; } }

  @GetMapping @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse status() {
    BackupService.Settings s = backups.settings();
    String effective = (backups.hostDir().isBlank() ? backups.baseDir().toString() : backups.hostDir()) + (s.subcarpeta().isBlank() ? "" : "/" + s.subcarpeta());
    return new StatusResponse(backups.running(), s.destinoLocal(), s.destinoDrive(), s.autoEnabled(), s.autoHour(), s.subcarpeta(), s.retentionDays(),
      new LocalInfo(backups.hostDir(), backups.baseDir().toString(), effective, backups.localWritable(s)),
      new DriveInfo(backups.driveConnected(s), s.driveAccount(), s.driveFolderName()), backups.history(10));
  }

  @PutMapping @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse save(@Valid @RequestBody SettingsRequest r) { backups.saveSettings(r.destinoLocal(), r.destinoDrive(), r.autoEnabled(), r.autoHour(), r.subcarpeta(), r.retentionDays()); return status(); }

  @PostMapping("/run") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  Map<String, Object> run() { backups.startManual(actor()); return Map.of("accepted", true); }

  /** Código del script de Google con la clave de esta instalación. Se copia desde Administración. */
  @GetMapping(value = "/drive/script", produces = "text/plain;charset=UTF-8") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  ResponseEntity<String> driveScript() {
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.TEXT_PLAIN).body(backups.driveScript());
  }

  @PutMapping("/drive/link") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse link(@Valid @RequestBody LinkRequest r) { backups.linkDrive(actor(), r.scriptUrl()); return status(); }

  @PostMapping("/drive/disconnect") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')")
  StatusResponse disconnect() { backups.disconnectDrive(actor()); return status(); }
}
