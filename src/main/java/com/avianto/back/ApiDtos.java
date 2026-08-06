package com.avianto.back;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ApiDtos {
  private ApiDtos() {}
  public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, String sortBy, String direction) {}
  public record ClientRequest(@NotBlank String nombre, String documento, @NotBlank String telefono, @Email String email, String direccion, String observaciones) {}
  public record ClientResponse(UUID id, String nombre, String documento, String telefono, String email, String direccion, String observaciones, boolean activo, long motos, long pedidos, Instant createdAt, Instant updatedAt) {}
  public record NameRequest(@NotBlank @Size(max=100) String nombre, Boolean activo) {}
  public record NamedResponse(UUID id, String nombre, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record UserRequest(@NotBlank String username, @NotBlank String nombre, @Email String email, @NotNull Role rol, Boolean activo, @Size(min=8,max=100) String password) {}
  public record UserResponse(UUID id, String nombre, String email, Role rol, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record MotorcycleRequest(@NotNull UUID clienteId, @NotNull UUID marcaId, @NotBlank String modelo, @NotBlank String patente, @Min(1900) @Max(2100) Integer anio, @PositiveOrZero Integer kilometraje, String color, String cilindrada, String observaciones) {}
  public record MotorcycleResponse(UUID id, UUID clienteId, UUID marcaId, String cliente, String marca, String modelo, String patente, Integer anio, Integer kilometraje, String color, String cilindrada, String observaciones, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record CatalogRequest(@NotBlank String descripcion, @NotNull ItemType tipo, @NotNull UUID categoriaId, @NotNull @DecimalMin("0.00") BigDecimal precioBase, String observaciones) {}
  public record CatalogResponse(UUID id, String descripcion, ItemType tipo, UUID categoriaId, String categoria, BigDecimal precioBase, String observaciones, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record OrderItemRequest(UUID itemCatalogoId, @NotBlank String descripcion, @NotNull ItemType tipo, @NotNull @DecimalMin(value="0.01") BigDecimal cantidad, @NotNull @DecimalMin("0.00") BigDecimal precioUnitario, @NotNull @DecimalMin("0.00") BigDecimal descuento) {}
  public record OrderItemResponse(UUID id, UUID itemCatalogoId, String descripcion, ItemType tipo, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal) {}
  public record OrderRequest(@NotNull UUID clienteId, @NotNull UUID motovehiculoId, @NotNull DocumentType documento, @NotNull LocalDate vencimiento, String observaciones, @NotNull @DecimalMin("0.00") BigDecimal descuentoGlobal, boolean iva, @NotEmpty List<@Valid OrderItemRequest> items) {}
  public record OrderResponse(UUID id, String numero, UUID clienteId, UUID motovehiculoId, String cliente, String moto, String patente, DocumentType documento, LocalDate vencimiento, String observaciones, BigDecimal descuentoGlobal, boolean iva, String estado, BigDecimal total, Instant creadoEn, List<OrderItemResponse> items, List<String> fotos) {}
  public record StateRequest(@NotBlank String estado) {}
  public record PhotoRequest(@NotBlank String filename, @NotBlank String contentType, @NotBlank String base64) {}
  public record PhotoResponse(UUID id, String filename, String contentType, Instant createdAt) {}
  public record AutocompleteResponse(UUID id, String label, String secondary) {}
  public record AuditResponse(UUID id, Instant fecha, String usuario, String modulo, String accion, String descripcion) {}
  public record ReportResponse(String etiqueta, BigDecimal valor) {}
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record SessionResponse(String accessToken, String refreshToken, UserResponse user) {}
}
