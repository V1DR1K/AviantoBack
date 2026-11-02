package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Google Drive simulado en local: valida el protocolo (OAuth, carpeta, subida reanudable, rotación). */
class GoogleDriveTest {
  private HttpServer server; private final GoogleDrive drive = new GoogleDrive(); private final List<String> calls = new CopyOnWriteArrayList<>(); private String uploaded; private boolean grantNoRefresh, folderGone;

  private static void reply(HttpExchange e, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().add("Content-Type", "application/json");
    e.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length); if (bytes.length > 0) e.getResponseBody().write(bytes); e.close();
  }

  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/token", e -> {
      String form = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); calls.add("token " + form);
      if (form.contains("refresh_token=revocado")) reply(e, 400, "{\"error\":\"invalid_grant\"}");
      else if (form.contains("grant_type=authorization_code")) reply(e, 200, grantNoRefresh ? "{\"access_token\":\"AT\"}" : "{\"access_token\":\"AT\",\"refresh_token\":\"RT\"}");
      else reply(e, 200, "{\"access_token\":\"AT2\"}");
    });
    server.createContext("/oauth2/v3/userinfo", e -> reply(e, 200, "{\"email\":\"taller@gmail.com\"}"));
    server.createContext("/upload/drive/v3/files", e -> {
      if (e.getRequestMethod().equals("POST")) {
        String meta = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); calls.add("start " + e.getRequestHeaders().getFirst("X-Upload-Content-Length") + " " + meta);
        if (folderGone) { reply(e, 404, "{\"error\":{\"message\":\"File not found\"}}"); return; }
        e.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/session/1"); reply(e, 200, "");
      } else reply(e, 405, "");
    });
    server.createContext("/session/1", e -> { uploaded = new String(e.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1); calls.add("put"); reply(e, 200, "{\"id\":\"FILE1\"}"); });
    server.createContext("/drive/v3/files", e -> {
      String method = e.getRequestMethod(), path = e.getRequestURI().getPath(); calls.add(method + " " + path);
      if (method.equals("POST")) reply(e, 200, "{\"id\":\"FOLDER1\"}");
      else if (method.equals("DELETE")) reply(e, 204, "");
      else if (path.endsWith("/FOLDER1")) reply(e, 200, "{\"id\":\"FOLDER1\",\"trashed\":false}");
      else if (path.endsWith("/BORRADA")) reply(e, 404, "{\"error\":{\"message\":\"not found\"}}");
      else reply(e, 200, "{\"files\":[{\"id\":\"n\",\"name\":\"avianto-3\",\"createdTime\":\"2026-03-01T00:00:00Z\"},{\"id\":\"v\",\"name\":\"avianto-2\",\"createdTime\":\"2026-01-01T00:00:00Z\"}]}");
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    drive.authBase = base; drive.tokenBase = base; drive.apiBase = base;
  }
  @AfterEach void stop() { server.stop(0); }

  @Test void authorizationUrlAsksForOfflineAccessToTheDriveFileScopeOnly() {
    String url = drive.authorizationUrl("cid", "http://localhost:8080/cb", "st4te");
    assertTrue(url.contains("access_type=offline")); assertTrue(url.contains("prompt=consent")); assertTrue(url.contains("state=st4te"));
    assertTrue(url.contains("drive.file")); assertFalse(url.contains("auth%2Fdrive&") || url.contains("auth/drive "), "no pide acceso a todo el Drive");
    assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcb"));
  }

  @Test void linkingReturnsTokensAccountAndCreatesTheFolder() throws Exception {
    GoogleDrive.Tokens tokens = drive.exchangeCode("cid", "sec", "http://localhost:8080/cb", "CODE");
    assertEquals("RT", tokens.refreshToken());
    assertEquals("taller@gmail.com", drive.accountEmail(tokens.accessToken()));
    assertEquals("FOLDER1", drive.createFolder(tokens.accessToken(), "Avianto Backups"));
    assertTrue(calls.stream().anyMatch(c -> c.startsWith("token ") && c.contains("code=CODE") && c.contains("client_secret=sec")));
  }

  @Test void missingRefreshTokenGivesAnActionableMessage() {
    grantNoRefresh = true;
    IOException e = assertThrows(IOException.class, () -> drive.exchangeCode("cid", "sec", "uri", "CODE"));
    assertTrue(e.getMessage().contains("Revocá el acceso"));
  }

  @Test void revokedRefreshTokenAsksToRelink() {
    DriveAuthException e = assertThrows(DriveAuthException.class, () -> drive.accessToken("cid", "sec", "revocado"));
    assertTrue(e.getMessage().contains("vincular"));
    assertDoesNotThrow(() -> drive.accessToken("cid", "sec", "vigente"));
  }

  @Test void resumableUploadSendsExactBytesIntoTheFolder(@TempDir Path dir) throws Exception {
    byte[] data = new byte[300_000]; new Random(1).nextBytes(data); Path file = dir.resolve("avianto-x.sql.gz"); Files.write(file, data);
    assertEquals("FILE1", drive.upload("AT", "FOLDER1", file, "avianto-x.sql.gz", data.length));
    assertEquals(data.length, uploaded.length());
    assertArrayEquals(data, uploaded.getBytes(StandardCharsets.ISO_8859_1));
    assertTrue(calls.stream().anyMatch(c -> c.startsWith("start 300000") && c.contains("\"parents\":[\"FOLDER1\"]")));
  }

  @Test void deletedFolderIsDetected(@TempDir Path dir) throws Exception {
    assertTrue(drive.folderExists("AT", "FOLDER1"));
    assertFalse(drive.folderExists("AT", "BORRADA"));
    folderGone = true; Path file = dir.resolve("a.gz"); Files.writeString(file, "x");
    assertThrows(DriveFolderMissingException.class, () -> drive.upload("AT", "FOLDER1", file, "a.gz", 1));
  }

  @Test void listsBackupsAndDeletesByIdWithoutTouchingOtherFiles() throws Exception {
    List<GoogleDrive.Remote> files = drive.listBackups("AT", "FOLDER1");
    assertEquals(2, files.size()); assertEquals(Instant.parse("2026-03-01T00:00:00Z"), files.get(0).created());
    drive.delete("AT", "v");
    assertTrue(calls.contains("DELETE /drive/v3/files/v"));
  }
}
