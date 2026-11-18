package com.avianto.back;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class DolarApiClient {
  private final RestClient restClient;

  DolarApiClient(RestClient.Builder builder,
      @Value("${app.exchange-rate.api-base-url:https://dolarapi.com}") String apiBaseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(3));
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    restClient = builder.baseUrl(apiBaseUrl).requestFactory(requestFactory).build();
  }

  List<DolarQuote> currentQuotes() {
    return restClient.get()
        .uri("/v1/dolares")
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  record DolarQuote(String casa, String nombre, BigDecimal compra, BigDecimal venta,
      String moneda, String fechaActualizacion) {}
}
