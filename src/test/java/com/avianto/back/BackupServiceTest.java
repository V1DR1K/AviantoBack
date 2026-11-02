package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

class BackupServiceTest {
  private BackupService service(Path dir, String pgDump) throws Exception {
    return new BackupService(null, new GoogleDrive(), dir.toString(), "C:/AviantoBackups", pgDump, "jdbc:postgresql://postgres:5432/avianto", "avianto", "secret", "http://localhost:8080/", "0123456789012345678901234567890123456789");
  }

  @Test void subfolderCannotEscapeTheMountedDirectory(@TempDir Path dir) throws Exception {
    BackupService service = service(dir, "pg_dump");
    assertEquals(dir.toAbsolutePath().normalize(), service.localDir(""));
    assertEquals(dir.toAbsolutePath().normalize().resolve("diarias"), service.localDir("diarias"));
    assertThrows(BusinessException.class, () -> service.localDir("../fuera"));
    assertThrows(BusinessException.class, () -> service.localDir(".."));
  }

  @Test void secretsRoundTripAndNeverStoreThePlainText(@TempDir Path dir) throws Exception {
    BackupService service = service(dir, "pg_dump");
    String encrypted = service.encrypt("1//refresh-token");
    assertNotEquals("1//refresh-token", encrypted);
    assertFalse(encrypted.contains("refresh"));
    assertEquals("1//refresh-token", service.decrypt(encrypted));
    assertNotEquals(encrypted, service.encrypt("1//refresh-token"), "cada cifrado usa un IV distinto");
    assertNull(service.decrypt("no-es-un-secreto-valido"));
    assertNull(service.decrypt(null));
  }

  @Test void redirectUriIgnoresTrailingSlash(@TempDir Path dir) throws Exception {
    assertEquals("http://localhost:8080/api/configuracion/backups/drive/callback", service(dir, "pg_dump").redirectUri());
  }

  @Test void dumpWritesAtomicGzipAndRejectsIncompleteOutput(@TempDir Path dir) throws Exception {
    assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"), "usa un pg_dump simulado en shell");
    Path ok = dir.resolve("pg_dump_ok"); Files.writeString(ok, "#!/bin/sh\necho 'SELECT 1;'\necho '-- PostgreSQL database dump complete'\necho '--'\necho ''\necho '\\unrestrict abc'\n"); ok.toFile().setExecutable(true);
    Path target = dir.resolve("out");
    Path file = service(dir, ok.toString()).dump(target, "avianto-20260101T000000Z.sql.gz");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) { assertEquals("SELECT 1;", reader.readLine()); }
    try (var files = Files.list(target)) { assertEquals(1, files.count(), "no deben quedar temporales"); }

    Path truncated = dir.resolve("pg_dump_cut"); Files.writeString(truncated, "#!/bin/sh\necho 'SELECT 1;'\n"); truncated.toFile().setExecutable(true);
    assertThrows(java.io.IOException.class, () -> service(dir, truncated.toString()).dump(dir.resolve("out2"), "avianto-20260101T000001Z.sql.gz"));
    try (var files = Files.list(dir.resolve("out2"))) { assertEquals(0, files.count()); }

    Path failing = dir.resolve("pg_dump_fail"); Files.writeString(failing, "#!/bin/sh\necho 'boom' >&2\nexit 1\n"); failing.toFile().setExecutable(true);
    java.io.IOException error = assertThrows(java.io.IOException.class, () -> service(dir, failing.toString()).dump(dir.resolve("out3"), "avianto-20260101T000002Z.sql.gz"));
    assertTrue(error.getMessage().contains("boom"));
  }
}
