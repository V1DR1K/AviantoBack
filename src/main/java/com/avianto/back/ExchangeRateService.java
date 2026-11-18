package com.avianto.back;

import static com.avianto.back.ApiDtos.ExchangeRateResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
class ExchangeRateService {
  private static final Duration CACHE_TTL = Duration.ofMinutes(1);
  private final DolarApiClient client;
  private volatile CachedQuotes cached;

  ExchangeRateService(DolarApiClient client) {
    this.client = client;
  }

  List<ExchangeRateResponse> currentRates() {
    CachedQuotes snapshot = cached;
    if (snapshot == null || snapshot.expiresAt().isBefore(Instant.now())) {
      synchronized (this) {
        snapshot = cached;
        if (snapshot == null || snapshot.expiresAt().isBefore(Instant.now())) {
          List<ExchangeRateResponse> rates = loadRates();
          snapshot = new CachedQuotes(rates, Instant.now().plus(CACHE_TTL));
          cached = snapshot;
        }
      }
    }
    return snapshot.rates();
  }

  private List<ExchangeRateResponse> loadRates() {
    try {
      List<DolarApiClient.DolarQuote> quotes = client.currentQuotes();
      List<ExchangeRateResponse> rates = new ArrayList<>();
      for (DolarApiClient.DolarQuote quote : quotes) {
        String reference = switch (quote.casa().toLowerCase(Locale.ROOT)) {
          case "oficial" -> "OFICIAL";
          case "blue" -> "BLUE";
          case "bolsa", "mep" -> "MEP";
          default -> null;
        };
        if (reference == null || quote.venta() == null || quote.venta().signum() <= 0) continue;
        Instant updatedAt = OffsetDateTime.parse(quote.fechaActualizacion()).toInstant();
        rates.add(new ExchangeRateResponse(reference, quote.nombre(), quote.compra(), quote.venta(), "DolarAPI", updatedAt));
      }
      if (rates.stream().noneMatch(rate -> rate.codigo().equals("OFICIAL"))
          || rates.stream().noneMatch(rate -> rate.codigo().equals("BLUE"))
          || rates.stream().noneMatch(rate -> rate.codigo().equals("MEP"))) {
        throw new IllegalStateException("Faltan cotizaciones para los tipos de cambio habilitados");
      }
      return List.copyOf(rates);
    } catch (Exception exception) {
      throw new BusinessException(503, "No se pudo consultar la cotización del dólar. Reintentá en unos minutos");
    }
  }

  private record CachedQuotes(List<ExchangeRateResponse> rates, Instant expiresAt) {}
}
