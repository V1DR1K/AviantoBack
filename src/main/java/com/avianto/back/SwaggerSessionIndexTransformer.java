package com.avianto.back;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Configuration
class SwaggerSessionUiConfiguration {
  @Bean
  SwaggerIndexTransformer swaggerIndexTransformer(
      SwaggerUiConfigProperties config,
      SwaggerUiOAuthProperties oauth,
      SwaggerWelcomeCommon welcome,
      ObjectMapperProvider objectMapperProvider) {
    return new SwaggerSessionIndexTransformer(
        new SwaggerIndexPageTransformer(config, oauth, welcome, objectMapperProvider));
  }
}

final class SwaggerSessionIndexTransformer implements SwaggerIndexTransformer {
  private static final String INTERCEPTOR = """
      requestInterceptor: async (request) => {
        const target = new URL(request.url, window.location.href);
        if (target.origin !== window.location.origin) return request;
        try {
          let session = JSON.parse(window.sessionStorage.getItem("avianto.session") || "null");
          if (!session || session.user?.rol !== "ADMINISTRACION") return request;
          let accessToken = session.accessToken;
          const encodedPayload = accessToken?.split(".")[1];
          if (encodedPayload) {
            const payload = JSON.parse(window.atob(encodedPayload.replace(/-/g, "+").replace(/_/g, "/")));
            if (typeof payload.exp === "number" && payload.exp * 1000 <= Date.now() + 30000 && session.refreshToken) {
              const response = await window.fetch(new URL("/api/auth/refresh", window.location.origin), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ refreshToken: session.refreshToken })
              });
              if (!response.ok) {
                window.sessionStorage.removeItem("avianto.session");
                return request;
              }
              session = await response.json();
              window.sessionStorage.setItem("avianto.session", JSON.stringify(session));
              if (session.user?.rol !== "ADMINISTRACION") return request;
              accessToken = session.accessToken;
            }
          }
          if (accessToken) {
            request.headers = request.headers || {};
            request.headers.Authorization = `Bearer ${accessToken}`;
          }
        } catch (error) {
          // Keep the request unauthenticated if the stored session cannot be read.
        }
        return request;
      },
      """;

  private final SwaggerIndexTransformer delegate;

  SwaggerSessionIndexTransformer(SwaggerIndexTransformer delegate) {
    this.delegate = delegate;
  }

  @Override
  public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
      throws IOException {
    Resource transformed = delegate.transform(request, resource, chain);
    if (!resource.getURL().toString().contains("swagger-initializer.js")) return transformed;

    String javascript = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String initializer = "SwaggerUIBundle({";
    int insertionPoint = javascript.indexOf(initializer);
    if (insertionPoint < 0) return transformed;

    int configurationStart = insertionPoint + initializer.length();
    String updated = javascript.substring(0, configurationStart) + "\n" + INTERCEPTOR
        + javascript.substring(configurationStart);
    updated = updated.replaceFirst(
        "(?m)^([\\t ]*)url:\\s*\"https://petstore\\.swagger\\.io/v2/swagger\\.json\",?",
        "$1url: \"/v3/api-docs\",");
    updated = updated.replaceAll(
        "(?m)^[\\t ]*\"configUrl\"\\s*:\\s*\"/v3/api-docs/swagger-config\",?[\\t ]*\\r?\\n?",
        "");
    return new TransformedResource(transformed, updated.getBytes(StandardCharsets.UTF_8));
  }
}
