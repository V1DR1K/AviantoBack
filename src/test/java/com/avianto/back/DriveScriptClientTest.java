package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Apps Script simulado en local: POST al script, 302 hacia el resultado que se lee con GET,
 * respuestas de error y subida en base64. No reemplaza probar el script real en Google.
 */
class DriveScriptClientTest {
  private HttpServer server; private String base; private final DriveScriptClient client = new DriveScriptClient();
  private final List<String> calls = new CopyOnWriteArrayList<>(); private final List<String> bodies = new CopyOnWriteArrayList<>();
  private final ObjectMapper json = new ObjectMapper();
  private String reply = "{\"ok\":true,\"folderName\":\"Avianto Backups\",\"account\":\"taller@gmail.com\"}"; private boolean html;

  private static void send(HttpExchange e, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().add("Content-Type", "application/json");
    e.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length); if (bytes.length > 0) e.getResponseBody().write(bytes); e.close();
  }

  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/macros/s/TEST/exec", e -> {
      if (!e.getRequestMethod().equals("POST")) { send(e, 405, ""); return; }
      bodies.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); calls.add("POST");
      e.getResponseHeaders().add("Location", base + "/echo/resultado"); send(e, 302, "");
    });
    server.createContext("/echo/resultado", e -> { calls.add("GET " + e.getRequestURI().getPath()); send(e, 200, html ? "<html>Iniciá sesión</html>" : reply); });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }
  @AfterEach void stop() { server.stop(0); }

  private String scriptUrl() { return base + "/macros/s/TEST/exec"; }

  @Test void pingFollowsTheRedirectWithGetAndSendsTheTokenInTheBody() throws Exception {
    DriveScriptClient.Result result = client.ping(scriptUrl(), "tok");
    assertEquals("Avianto Backups", result.folderName());
    assertEquals("taller@gmail.com", result.account());
    assertEquals(List.of("POST", "GET /echo/resultado"), calls);
    JsonNode body = json.readTree(bodies.get(0));
    assertEquals("tok", body.path("token").asText()); assertEquals("ping", body.path("action").asText());
    assertFalse(scriptUrl().contains("tok"), "la clave no va en la URL");
  }

  @Test void uploadSendsTheBackupAsBase64WithRetentionSettings(@TempDir Path dir) throws Exception {
    byte[] data = new byte[120_000]; new Random(7).nextBytes(data); Path file = dir.resolve("avianto-20260101T000000Z.sql.gz"); Files.write(file, data);
    client.upload(scriptUrl(), "tok", "avianto-20260101T000000Z.sql.gz", file, 14, 3);
    JsonNode body = json.readTree(bodies.get(0));
    assertEquals("upload", body.path("action").asText()); assertEquals("avianto-20260101T000000Z.sql.gz", body.path("name").asText());
    assertArrayEquals(data, Base64.getDecoder().decode(body.path("dataBase64").asText()));
    assertEquals(14, body.path("retentionDays").asInt()); assertEquals(3, body.path("keepNewest").asInt());
  }

  @Test void wrongTokenAsksToCopyTheScriptAgain() {
    reply = "{\"ok\":false,\"code\":\"TOKEN\",\"error\":\"La clave no coincide con la de Avianto.\"}";
    DriveAuthException e = assertThrows(DriveAuthException.class, () -> client.ping(scriptUrl(), "otra"));
    assertTrue(e.getMessage().contains("La clave no coincide"));
  }

  @Test void scriptErrorsAreReportedAsTheyAre() {
    reply = "{\"ok\":false,\"code\":\"SIZE\",\"error\":\"La copia es demasiado grande para Google Apps Script.\"}";
    IOException e = assertThrows(IOException.class, () -> client.ping(scriptUrl(), "tok"));
    assertTrue(e.getMessage().contains("demasiado grande"));
  }

  @Test void anHtmlPageInsteadOfJsonMeansTheWebAppIsNotPublicYet() {
    html = true;
    IOException e = assertThrows(IOException.class, () -> client.ping(scriptUrl(), "tok"));
    assertTrue(e.getMessage().contains("Cualquier persona"));
  }
}
