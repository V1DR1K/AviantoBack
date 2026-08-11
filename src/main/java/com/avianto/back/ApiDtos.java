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
  public record ClientResponse(UUID id, String nombre, String documento, String telefono, String email, String direccion, String observaciones, boolean activo, long motos, long fichas, Instant createdAt, Instant updatedAt) {}
  public record NameRequest(@NotBlank @Size(max=120) String nombre, Boolean activo) {}
  public record NamedResponse(UUID id, String nombre, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record UserRequest(@NotBlank String username, @NotBlank String nombre, @Email String email, @NotNull Role rol, Boolean activo, @Size(min=8,max=100) String password) {}
  public record UserResponse(UUID id, String nombre, String email, Role rol, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record MotorcycleRequest(UUID clienteId, @NotNull UUID marcaId, @NotBlank String modelo, @NotBlank String patente, @Min(1900) @Max(2100) Integer anio, @PositiveOrZero Integer kilometraje, String observaciones) {}
  public record MotorcycleResponse(UUID id, UUID propietarioId, String propietario, UUID marcaId, String marca, String modelo, String patente, Integer anio, Integer kilometraje, String seccion, boolean ingresada, String estado, Integer kmUltimoService, LocalDate fechaUltimoService, Integer kmServicePeriodo, Integer mesesServicePeriodo, String serviceObservaciones, String observaciones, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record ProfileRequest(@NotNull UUID marcaId, @NotBlank String modelo, @NotBlank String patente, @Min(1900) @Max(2100) Integer anio, @PositiveOrZero Integer kilometraje, String observaciones, UUID clienteId, String clienteNombre, String clienteTelefono) {}
  public record ProfileResponse(UUID id, UUID propietarioId, String propietario, UUID marcaId, String marca, String modelo, String patente, Integer anio, Integer kilometraje, String seccion, boolean ingresada, String estado, Integer kmUltimoService, LocalDate fechaUltimoService, Integer kmServicePeriodo, Integer mesesServicePeriodo, String serviceObservaciones, String observaciones, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record IntakeRequest(@NotBlank String seccion) {}
  public record MotoConfigServiceRequest(@PositiveOrZero Integer kmServicePeriodo, @PositiveOrZero Integer mesesServicePeriodo, String serviceObservaciones) {}
  public record TransferUpdateRequest(@NotNull UUID clienteNuevoId, @NotNull LocalDate fechaTransferencia, String observaciones) {}
  public record TrabajoCatalogoRequest(@NotBlank @Size(max=300) String descripcion, @NotNull @DecimalMin("0.00") BigDecimal precioBase, Boolean activo) {}
  public record TrabajoCatalogoResponse(UUID id, String descripcion, BigDecimal precioBase, boolean activo, Instant createdAt, Instant updatedAt) {}
  public record FichaTrabajoRequest(@NotBlank String descripcion, @NotNull @DecimalMin("0.00") BigDecimal precioUnitario, @DecimalMin("0.00") BigDecimal descuento, String estadoTrabajo, String observacionTrabajo, UUID id) {
    public FichaTrabajoRequest(String descripcion, BigDecimal precioUnitario, BigDecimal descuento, String estadoTrabajo, String observacionTrabajo) { this(descripcion, precioUnitario, descuento, estadoTrabajo, observacionTrabajo, null); }
  }
  public record FichaTrabajoResponse(UUID id, String descripcion, BigDecimal precioUnitario, BigDecimal descuento, BigDecimal subtotal, String estadoTrabajo, String observacionTrabajo, Instant completadoAt, UUID completadoPor, boolean pagado) {}
  public record FichaRequest(@NotNull UUID clienteId, @NotNull UUID motoId, LocalDate fechaIngreso, LocalDate fechaEntregaEstimada, @PositiveOrZero Integer kilometrajeIngreso, LocalDate vencimiento, String observaciones, @NotNull @DecimalMin("0.00") BigDecimal descuentoGlobal, boolean iva, @Valid List<FichaTrabajoRequest> trabajos) {}
  public record FichaResponse(UUID id, String numero, UUID clienteId, UUID motoId, String cliente, String moto, String patente, LocalDate vencimiento, LocalDate fechaIngreso, LocalDate fechaEntregaEstimada, LocalDate fechaEntregaReal, Integer kilometrajeIngreso, String observaciones, BigDecimal descuentoGlobal, boolean iva, String estado, String estadoPago, BigDecimal total, Instant creadoEn, List<FichaTrabajoResponse> trabajos, List<PhotoResponse> fotos) {}
  public record StateRequest(@NotBlank String estado) {}
  public record PagoRequest(@NotBlank String estadoPago, List<UUID> itemIds) {}
  public record PhotoRequest(@NotBlank String filename, @NotBlank String contentType, @NotBlank String base64) {}
  public record PhotoResponse(UUID id, String filename, String contentType, Instant createdAt, String url) {}
  public record OwnerRequest(@NotNull UUID clienteId, LocalDate fechaDesde, String observaciones) {}
  public record OwnerResponse(UUID id, UUID clienteId, String cliente, LocalDate fechaDesde, LocalDate fechaHasta, boolean actual, String observaciones) {}
  public record TransferRequest(@NotNull UUID motoId, @NotNull UUID clienteNuevoId, @NotNull LocalDate fechaTransferencia, String observaciones) {}
  public record TransferResponse(UUID id, UUID motoId, String patente, String moto, UUID clienteAnteriorId, String clienteAnterior, UUID clienteNuevoId, String clienteNuevo, LocalDate fechaTransferencia, String observaciones, String realizadaPor, Instant createdAt) {}
  public record ServiceRequest(UUID fichaId, @NotNull Integer kilometraje, LocalDate fecha, String observaciones) {}
  public record ServiceResponse(UUID id, UUID motoId, UUID fichaId, String fichaNumero, Integer kilometraje, LocalDate fecha, String observaciones, String realizadoPor, Instant creadoEn) {}
  public record NextServiceResponse(UUID motoId, String patente, String cliente, String moto, Integer kilometraje, Integer kmUltimoService, LocalDate fechaUltimoService, Integer kmServicePeriodo, Integer mesesServicePeriodo, Integer proximKm, Integer kmFaltan, LocalDate proximaFecha, Long diasFaltan, boolean atrasadoKm, boolean atrasadoFecha, boolean sinReferencia) {}
  public record RepuestoItemRequest(@NotBlank String descripcion, @NotNull RepuestoCategoria tipo, UUID fichaTrabajoId, @NotNull @Positive @DecimalMin("0.00") BigDecimal cantidad, @NotNull @DecimalMin("0.00") BigDecimal precio, String estado, String observaciones, UUID id) {
    public RepuestoItemRequest(String descripcion, RepuestoCategoria tipo, UUID fichaTrabajoId, BigDecimal cantidad, BigDecimal precio, String estado, String observaciones) { this(descripcion, tipo, fichaTrabajoId, cantidad, precio, estado, observaciones, null); }
  }
  public record RepuestoItemResponse(UUID id, UUID fichaTrabajoId, String descripcion, RepuestoCategoria tipo, BigDecimal cantidad, BigDecimal precio, BigDecimal subtotal, String estado, String observaciones, boolean pagado) {}
  public record RepuestoRequest(@NotNull UUID motoVehiculoId, @NotNull UUID clienteId, UUID fichaId, LocalDate fecha, String proveedor, String observaciones, @NotEmpty List<@Valid RepuestoItemRequest> items) {}
  public record RepuestoResponse(UUID id, String numero, UUID motoId, String patente, UUID clienteId, String cliente, UUID fichaId, LocalDate fecha, String estado, String estadoPago, BigDecimal total, String proveedor, String observaciones, List<RepuestoItemResponse> items, Instant creadoEn) {}
  public record ControlRequest(@NotBlank @Size(max=200) String nombre, String descripcion, Boolean obligatorio, Integer orden, Boolean activo, List<UUID> categoriaIds) {}
  public record ControlResponse(UUID id, String nombre, String descripcion, boolean obligatorio, int orden, boolean activo, List<NamedResponse> categorias, Instant createdAt, Instant updatedAt) {}
  public record RevisionControlRequest(String estado, String observacion, String correccionNecesaria) {}
  public record RevisionControlResponse(UUID id, UUID controlId, String control, String categorias, boolean obligatorio, int orden, String estado, String observacion, String correccionNecesaria, String revisadoPor, Instant revisadoAt) {}
  public record RevisionAprobarRequest(boolean forzada, String observacion, List<UUID> serviceIds) {}
  public record RevisionResponse(UUID id, UUID fichaId, String ficha, String estado, String aprobadoPor, Instant aprobadoAt, boolean forzada, String observacion, List<RevisionControlResponse> controles) {}
  public record AutocompleteResponse(UUID id, String label, String secondary) {}
  public record AuditResponse(UUID id, Instant fecha, String usuario, String modulo, String accion, String descripcion) {}
  public record ReportResponse(String etiqueta, BigDecimal valor) {}
  public record DashboardOrderResponse(UUID id, String numero, String cliente, String moto, String estado, BigDecimal total, Instant createdAt) {}
  public record DashboardResponse(LocalDate fechaDesde, LocalDate fechaHasta, long fichas, List<DashboardOrderResponse> recientes) {}
  public record TallerMotoResponse(UUID motoId, String patente, String moto, String cliente, Integer kilometraje, UUID fichaId, String fichaNumero, String estado, LocalDate fechaIngreso) {}
  public record TallerEstadoResponse(String estado, List<TallerMotoResponse> motos) {}
  public record TallerResponse(List<TallerEstadoResponse> estados) {}
  public record DashboardFichaResponse(UUID id, String numero, String cliente, String moto, String patente, String estado, BigDecimal total, LocalDate fechaIngreso) {}
  public record DashboardFichaEstadoResponse(String estado, List<DashboardFichaResponse> fichas) {}
  public record DashboardFichasResponse(List<DashboardFichaEstadoResponse> estados) {}
  public record VentaMotoResponse(UUID motoId, String patente, String moto, String cliente, Integer kilometraje, String estado, LocalDate fechaIngreso) {}
  public record VentaEstadoResponse(String estado, List<VentaMotoResponse> motos) {}
  public record VentaResponse(List<VentaEstadoResponse> estados) {}
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record SessionResponse(String accessToken, String refreshToken, UserResponse user) {}
}
