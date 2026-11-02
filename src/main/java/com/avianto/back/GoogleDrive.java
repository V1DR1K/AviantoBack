package com.avianto.back;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** La conexión con Google se perdió (token revocado o credenciales cambiadas): hay que volver a vincular. */
class DriveAuthException extends IOException { DriveAuthException(String message) { super(message); } }
/** La carpeta de Drive ya no existe (por ejemplo, la borraron a mano). */
class DriveFolderMissingException extends IOException { DriveFolderMissingException() { super("La carpeta de Google Drive ya no existe."); } }

/**
 * Cliente mínimo de Google Drive v3 sobre OAuth 2.0 (flujo de usuario, scope drive.file).
 * Con drive.file la app solo ve lo que ella misma creó: por eso crea su propia carpeta.
 */
@Component
class GoogleDrive {
  static final String SCOPE = "https://www.googleapis.com/auth/drive.file openid email";
  record Tokens(String accessToken, String refreshToken) {}
  record Remote(String id, String name, Instant created) {}

  // Sobrescribibles solo en tests para apuntar a un servidor simulado.
  String authBase = "https://accounts.google.com", tokenBase = "https://oauth2.googleapis.com", apiBase = "https://www.googleapis.com";
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  private final ObjectMapper json = new ObjectMapper();

  private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  private static String form(Map<String, String> fields) { return fields.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&")); }

  String authorizationUrl(String clientId, String redirectUri, String state) {
    return authBase + "/o/oauth2/v2/auth?" + form(Map.of("client_id", clientId, "redirect_uri", redirectUri, "response_type", "code", "scope", SCOPE, "access_type", "offline", "prompt", "consent", "include_granted_scopes", "false", "state", state));
  }

  Tokens exchangeCode(String clientId, String clientSecret, String redirectUri, String code) throws IOException {
    JsonNode body = send(HttpRequest.newBuilder(URI.create(tokenBase + "/token")).header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("client_id", clientId, "client_secret", clientSecret, "code", code, "redirect_uri", redirectUri, "grant_type", "authorization_code")))).timeout(Duration.ofSeconds(30)).build(), true);
    String refresh = body.path("refresh_token").asText(null);
    if (refresh == null) throw new IOException("Google no entregó permiso de acceso permanente. Revocá el acceso de Avianto en tu cuenta de Google y volvé a vincular.");
    return new Tokens(body.path("access_token").asText(), refresh);
  }

  String accessToken(String clientId, String clientSecret, String refreshToken) throws IOException {
    JsonNode body = send(HttpRequest.newBuilder(URI.create(tokenBase + "/token")).header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("client_id", clientId, "client_secret", clientSecret, "refresh_token", refreshToken, "grant_type", "refresh_token")))).timeout(Duration.ofSeconds(30)).build(), true);
    return body.path("access_token").asText();
  }

  String accountEmail(String accessToken) throws IOException {
    return send(authorized(apiBase + "/oauth2/v3/userinfo", accessToken).GET().build(), false).path("email").asText("cuenta de Google");
  }

  void revoke(String token) {
    try { http.send(HttpRequest.newBuilder(URI.create(tokenBase + "/revoke")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token", token)))).timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { }
  }

  String createFolder(String accessToken, String name) throws IOException {
    String metadata = json.writeValueAsString(Map.of("name", name, "mimeType", "application/vnd.google-apps.folder"));
    return send(authorized(apiBase + "/drive/v3/files?fields=id", accessToken).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(metadata)).build(), false).path("id").asText();
  }

  boolean folderExists(String accessToken, String folderId) throws IOException {
    try { return !send(authorized(apiBase + "/drive/v3/files/" + enc(folderId) + "?fields=id,trashed", accessToken).GET().build(), false).path("trashed").asBoolean(false); }
    catch (DriveFolderMissingException e) { return false; }
  }

  /** Subida reanudable: soporta archivos grandes (las fotos viajan dentro de la base). */
  String upload(String accessToken, String folderId, Path file, String name, long size) throws IOException, InterruptedException {
    String metadata = json.writeValueAsString(Map.of("name", name, "parents", List.of(folderId)));
    HttpResponse<String> start = http.send(authorized(apiBase + "/upload/drive/v3/files?uploadType=resumable&fields=id", accessToken)
      .header("Content-Type", "application/json; charset=UTF-8").header("X-Upload-Content-Type", "application/gzip").header("X-Upload-Content-Length", String.valueOf(size))
      .POST(HttpRequest.BodyPublishers.ofString(metadata)).build(), HttpResponse.BodyHandlers.ofString());
    if (start.statusCode() == 404) throw new DriveFolderMissingException();
    check(start.statusCode(), start.body());
    String location = start.headers().firstValue("Location").orElseThrow(() -> new IOException("Google Drive no devolvió la dirección de subida."));
    HttpResponse<String> done = http.send(HttpRequest.newBuilder(URI.create(location)).header("Content-Type", "application/gzip").timeout(Duration.ofMinutes(60)).PUT(HttpRequest.BodyPublishers.ofFile(file)).build(), HttpResponse.BodyHandlers.ofString());
    check(done.statusCode(), done.body());
    return json.readTree(done.body()).path("id").asText();
  }

  List<Remote> listBackups(String accessToken, String folderId) throws IOException {
    String query = "'" + folderId + "' in parents and trashed=false and name contains 'avianto-'";
    JsonNode body = send(authorized(apiBase + "/drive/v3/files?pageSize=1000&orderBy=createdTime%20desc&fields=files(id,name,createdTime)&q=" + enc(query), accessToken).GET().build(), false);
    List<Remote> files = new ArrayList<>();
    body.path("files").forEach(n -> files.add(new Remote(n.path("id").asText(), n.path("name").asText(), Instant.parse(n.path("createdTime").asText()))));
    return files;
  }

  void delete(String accessToken, String fileId) throws IOException {
    send(authorized(apiBase + "/drive/v3/files/" + enc(fileId), accessToken).DELETE().build(), false);
  }

  private HttpRequest.Builder authorized(String url, String accessToken) { return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(60)); }

  private void check(int status, String body) throws IOException {
    if (status / 100 == 2) return;
    if (status == 401) throw new DriveAuthException("Google rechazó el acceso. Volvé a vincular la cuenta.");
    String detail = body;
    try { JsonNode error = json.readTree(body).path("error"); detail = error.isTextual() ? error.asText() : error.path("message").asText(body); } catch (Exception ignored) { }
    throw new IOException("Google Drive respondió " + status + ": " + (detail.length() > 200 ? detail.substring(0, 200) : detail));
  }

  private JsonNode send(HttpRequest request, boolean oauth) throws IOException {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (oauth && response.statusCode() >= 400) {
        String error = ""; try { error = json.readTree(response.body()).path("error").asText(); } catch (Exception ignored) { }
        if (error.equals("invalid_grant")) throw new DriveAuthException("La vinculación con Google venció o fue revocada. Volvé a vincular la cuenta.");
        if (error.equals("invalid_client") || error.equals("unauthorized_client")) throw new DriveAuthException("Google no reconoce el ID de cliente o el secreto. Revisalos en el paso 1.");
      }
      if (response.statusCode() == 404) throw new DriveFolderMissingException();
      check(response.statusCode(), response.body());
      return response.body() == null || response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Operación interrumpida."); }
  }
}
