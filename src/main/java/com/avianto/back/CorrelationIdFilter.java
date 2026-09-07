package com.avianto.back;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class CorrelationIdFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Correlation-Id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String correlationId = request.getHeader(HEADER);
    if (correlationId == null || !correlationId.matches("[A-Za-z0-9._-]{1,80}")) correlationId = UUID.randomUUID().toString();
    try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
      response.setHeader(HEADER, correlationId);
      chain.doFilter(request, response);
    }
  }
}
