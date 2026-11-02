package com.avianto.back;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** La clave del script no coincide: hay que volver a copiar el script desde Administración. */
class DriveAuthException extends IOException { DriveAuthException(String message) { super(message); } }

/**
 * Cliente de la aplicación web de Google Apps Script que guarda las copias en el Drive de la cuenta
 * que la publicó. No usa OAuth: la protección es la clave que Avianto incluye en el script.
 * Apps Script responde siempre 200 y redirige con 302 al resultado, que se lee con un GET explícito.
 */
@Component
class DriveScriptClient {
  static final long MAX_UPLOAD_BYTES = 35L * 1024 * 1024;
  record Result(String folderName, String account) {}

  private static final String NO_RESPONDE = "Google no devolvió la respuesta del script. Revisá que la aplicación web esté implementada con acceso «Cualquier persona» y que la dirección termine en /exec.";
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
  private final ObjectMapper json = new ObjectMapper();

  Result ping(String scriptUrl, String token) throws IOException {
    return call(scriptUrl, Map.of("token", token, "action", "ping"));
  }

  Result upload(String scriptUrl, String token, String name, Path file, int retentionDays, int keepNewest) throws IOException {
    if (Files.size(file) > MAX_UPLOAD_BYTES) throw new IOException("La copia pesa más de 35 MB y Google Apps Script no puede recibirla.");
    String data = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    return call(scriptUrl, Map.of("token", token, "action", "upload", "name", name, "dataBase64", data, "retentionDays", retentionDays, "keepNewest", keepNewest));
  }

  private Result call(String scriptUrl, Map<String, ?> body) throws IOException {
    try {
      URI target = URI.create(scriptUrl);
      HttpResponse<String> response = http.send(HttpRequest.newBuilder(target).header("Content-Type", "text/plain; charset=utf-8").timeout(Duration.ofMinutes(5))
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 == 3) {
        URI next = response.headers().firstValue("Location").map(location -> target.resolve(location)).orElseThrow(() -> new IOException(NO_RESPONDE));
        response = http.send(HttpRequest.newBuilder(next).timeout(Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
      }
      return parse(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); throw new IOException("Operación interrumpida.");
    } catch (IllegalArgumentException e) {
      throw new IOException(NO_RESPONDE);
    }
  }

  private Result parse(String raw) throws IOException {
    JsonNode body;
    try { body = json.readTree(raw); } catch (IOException e) { throw new IOException(NO_RESPONDE); }
    if (body == null || !body.isObject()) throw new IOException(NO_RESPONDE);
    if (!body.path("ok").asBoolean(false)) {
      String error = body.path("error").asText("El script devolvió un error.");
      if ("TOKEN".equals(body.path("code").asText())) throw new DriveAuthException(error);
      throw new IOException(error);
    }
    String account = body.path("account").asText("");
    return new Result(body.path("folderName").asText("Avianto Backups"), account.isBlank() ? "cuenta de Google" : account);
  }
}
