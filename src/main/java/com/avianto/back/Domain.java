package com.avianto.back;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

enum Role { ADMINISTRACION, OPERARIO }
enum ItemType { Pieza, Trabajo }
enum DocumentType { Presupuesto, Factura }
enum FichaState { INGRESADA, EN_TRABAJO, PARA_CONTROL, PARA_ENTREGA, ENTREGADA, CANCELADA;
  static FichaState of(String v) { return switch (v) { case "Ingresada" -> INGRESADA; case "En trabajo" -> EN_TRABAJO; case "Para control" -> PARA_CONTROL; case "Para entrega" -> PARA_ENTREGA; case "Entregada" -> ENTREGADA; case "Cancelada" -> CANCELADA; default -> throw new BusinessException(422, "Estado inválido"); }; }
  String label() { return switch(this) { case INGRESADA -> "Ingresada"; case EN_TRABAJO -> "En trabajo"; case PARA_CONTROL -> "Para control"; case PARA_ENTREGA -> "Para entrega"; case ENTREGADA -> "Entregada"; case CANCELADA -> "Cancelada"; }; }
}
enum PagoState { PENDIENTE, PARCIAL, PAGADO;
  static PagoState of(String v) { return switch (v) { case "Pendiente" -> PENDIENTE; case "Parcial" -> PARCIAL; case "Pagado" -> PAGADO; default -> throw new BusinessException(422, "Estado de pago inválido"); }; }
  String label() { return switch(this) { case PENDIENTE -> "Pendiente"; case PARCIAL -> "Parcial"; case PAGADO -> "Pagado"; }; }
}
enum TrabajoState { PENDIENTE, EN_PROCESO, REALIZADO, CANCELADO;
  static TrabajoState of(String v) { return switch (v) { case "Pendiente" -> PENDIENTE; case "En proceso" -> EN_PROCESO; case "Realizado" -> REALIZADO; case "Cancelado" -> CANCELADO; default -> throw new BusinessException(422, "Estado de trabajo inválido"); }; }
  String label() { return switch(this) { case PENDIENTE -> "Pendiente"; case EN_PROCESO -> "En proceso"; case REALIZADO -> "Realizado"; case CANCELADO -> "Cancelado"; }; }
}
enum EstadoMoto { ACTIVA, EN_TALLER, PARA_ENTREGA;
  static EstadoMoto of(String v) { return switch (v) { case "Activa" -> ACTIVA; case "En taller" -> EN_TALLER; case "Para entrega" -> PARA_ENTREGA; default -> throw new BusinessException(422, "Estado de moto inválido"); }; }
  String label() { return switch(this) { case ACTIVA -> "Activa"; case EN_TALLER -> "En taller"; case PARA_ENTREGA -> "Para entrega"; }; }
}
enum RepuestoItemType { Repuesto, Accesorio }
enum RepuestoItemState { PENDIENTE_DE_PEDIR, PEDIDO, RECIBIDO, ENTREGADO, CANCELADO;
  static RepuestoItemState of(String v) { return switch (v) { case "Pendiente de pedir" -> PENDIENTE_DE_PEDIR; case "Pedido" -> PEDIDO; case "Recibido" -> RECIBIDO; case "Entregado" -> ENTREGADO; case "Cancelado" -> CANCELADO; default -> throw new BusinessException(422, "Estado de ítem inválido"); }; }
  String label() { return switch(this) { case PENDIENTE_DE_PEDIR -> "Pendiente de pedir"; case PEDIDO -> "Pedido"; case RECIBIDO -> "Recibido"; case ENTREGADO -> "Entregado"; case CANCELADO -> "Cancelado"; }; }
}
enum RepuestoPedidoState { EN_CURSO, COMPLETADO, CANCELADO;
  static RepuestoPedidoState of(String v) { return switch (v) { case "En curso" -> EN_CURSO; case "Completado" -> COMPLETADO; case "Cancelado" -> CANCELADO; default -> throw new BusinessException(422, "Estado de pedido inválido"); }; }
  String label() { return switch(this) { case EN_CURSO -> "En curso"; case COMPLETADO -> "Completado"; case CANCELADO -> "Cancelado"; }; }
}
enum RepuestoPagoState { NO_PAGADO, PAGO_PARCIAL, PAGADO;
  static RepuestoPagoState of(String v) { return switch (v) { case "No pagado" -> NO_PAGADO; case "Pago parcial" -> PAGO_PARCIAL; case "Pagado" -> PAGADO; default -> throw new BusinessException(422, "Estado de pago inválido"); }; }
  String label() { return switch(this) { case NO_PAGADO -> "No pagado"; case PAGO_PARCIAL -> "Pago parcial"; case PAGADO -> "Pagado"; }; }
}
enum RevisionState { ABIERTA, APROBADA }
enum RevisionControlState { PENDIENTE, APROBADO, REQUIERE_CORRECCION, NO_APLICA;
  static RevisionControlState of(String v) { return switch (v) { case "Pendiente" -> PENDIENTE; case "Aprobado" -> APROBADO; case "Requiere corrección" -> REQUIERE_CORRECCION; case "No aplica" -> NO_APLICA; default -> throw new BusinessException(422, "Estado de control inválido"); }; }
  String label() { return switch(this) { case PENDIENTE -> "Pendiente"; case APROBADO -> "Aprobado"; case REQUIERE_CORRECCION -> "Requiere corrección"; case NO_APLICA -> "No aplica"; }; }
}

@MappedSuperclass abstract class BaseEntity {
  @Id UUID id = UUID.randomUUID();
  @Column(name="created_at", nullable=false) Instant createdAt = Instant.now();
  @Column(name="updated_at", nullable=false) Instant updatedAt = Instant.now();
  @Column(name="deleted_at") Instant deletedAt;
  @Column(name="deleted_by") UUID deletedBy;
  @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
}
@Entity @Table(name="app_user") class AppUser extends BaseEntity { String nombre; String username; String email; @Column(name="password_hash") String passwordHash; @Enumerated(EnumType.STRING) Role rol; boolean activo=true; }
@Entity class MarcaMoto extends BaseEntity { String nombre; boolean activo=true; }
@Entity class CategoriaCatalogo extends BaseEntity { String nombre; boolean activo=true; }
@Entity class Cliente extends BaseEntity { String nombre; String documento; String telefono; String email; String direccion; @Column(columnDefinition="text") String observaciones; boolean activo=true; }
@Entity class Motovehiculo extends BaseEntity { @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="cliente_id",nullable=false) Cliente cliente; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="marca_id",nullable=false) MarcaMoto marca; String modelo; String patente; Integer anio; Integer kilometraje; @Enumerated(EnumType.STRING) EstadoMoto estado=EstadoMoto.ACTIVA; @Column(name="km_ultimo_service") Integer kmUltimoService; @Column(name="fecha_ultimo_service") LocalDate fechaUltimoService; @Column(name="km_service_periodo") Integer kmServicePeriodo=5000; @Column(name="meses_service_periodo") Integer mesesServicePeriodo=6; @Column(name="service_observaciones",columnDefinition="text") String serviceObservaciones; @Column(columnDefinition="text") String observaciones; boolean activo=true; }
@Entity @Table(name="item_catalogo") class ItemCatalogo extends BaseEntity { String descripcion; @Enumerated(EnumType.STRING) ItemType tipo; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="categoria_id",nullable=false) CategoriaCatalogo categoria; @Column(name="precio_base") BigDecimal precioBase; @Column(columnDefinition="text") String observaciones; boolean activo=true; }
@Entity @Table(name="price_history") class PriceHistory { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="item_id") ItemCatalogo item; @Column(name="precio_anterior") BigDecimal precioAnterior; @Column(name="precio_nuevo") BigDecimal precioNuevo; @Column(name="changed_at") Instant changedAt=Instant.now(); @Column(name="changed_by") UUID changedBy; }
@Entity class Pedido extends BaseEntity { @Column(unique=true) String numero; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="cliente_id",nullable=false) Cliente cliente; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="motovehiculo_id",nullable=false) Motovehiculo motovehiculo; @Enumerated(EnumType.STRING) DocumentType documento; LocalDate vencimiento; @Column(name="fecha_ingreso") LocalDate fechaIngreso; @Column(name="fecha_entrega_estimada") LocalDate fechaEntregaEstimada; @Column(name="fecha_entrega_real") LocalDate fechaEntregaReal; @Column(name="kilometraje_ingreso") Integer kilometrajeIngreso; @Column(columnDefinition="text") String observaciones; @Column(name="descuento_global") BigDecimal descuentoGlobal=BigDecimal.ZERO; boolean iva; @Enumerated(EnumType.STRING) @Column(name="estado") FichaState estado=FichaState.INGRESADA; @Enumerated(EnumType.STRING) @Column(name="estado_pago") PagoState estadoPago=PagoState.PENDIENTE; BigDecimal total=BigDecimal.ZERO; @OneToMany(mappedBy="pedido",cascade=CascadeType.ALL,orphanRemoval=true) List<PedidoItem> items=new ArrayList<>(); @OneToMany(mappedBy="pedido",cascade=CascadeType.ALL,orphanRemoval=true) List<PedidoFoto> fotos=new ArrayList<>(); }
@Entity @Table(name="pedido_item") class PedidoItem { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pedido_id") Pedido pedido; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="item_catalogo_id") ItemCatalogo itemCatalogo; String descripcion; @Enumerated(EnumType.STRING) ItemType tipo; BigDecimal cantidad; @Column(name="precio_unitario") BigDecimal precioUnitario; BigDecimal descuento; BigDecimal subtotal; @Enumerated(EnumType.STRING) @Column(name="estado_trabajo") TrabajoState estadoTrabajo=TrabajoState.PENDIENTE; @Column(name="observacion_trabajo",columnDefinition="text") String observacionTrabajo; @Column(name="completado_at") Instant completadoAt; @Column(name="completado_por") UUID completadoPor; }
@Entity @Table(name="pedido_foto") class PedidoFoto { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pedido_id") Pedido pedido; String filename; @Column(name="content_type") String contentType; @JdbcTypeCode(Types.VARBINARY) byte[] content; @Column(name="created_at") Instant createdAt=Instant.now(); }
@Entity @Table(name="propietario_moto") class PropietarioMoto extends BaseEntity { @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="motovehiculo_id",nullable=false) Motovehiculo motovehiculo; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="cliente_id",nullable=false) Cliente cliente; @Column(name="fecha_desde") LocalDate fechaDesde; @Column(name="fecha_hasta") LocalDate fechaHasta; @Column(columnDefinition="text") String observaciones; }
@Entity @Table(name="service_moto") class ServiceMoto { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="motovehiculo_id",nullable=false) Motovehiculo motovehiculo; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="ficha_id") Pedido ficha; @Column(name="kilometraje") Integer kilometraje; LocalDate fecha; @Column(columnDefinition="text") String observaciones; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="realizado_por") AppUser realizadoPor; @Column(name="created_at") Instant createdAt=Instant.now(); }
@Entity @Table(name="pedido_repuesto") class PedidoRepuesto extends BaseEntity { @Column(unique=true) String numero; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="motovehiculo_id",nullable=false) Motovehiculo motovehiculo; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="cliente_id",nullable=false) Cliente cliente; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="ficha_id") Pedido ficha; LocalDate fecha; @Enumerated(EnumType.STRING) RepuestoPedidoState estado=RepuestoPedidoState.EN_CURSO; @Enumerated(EnumType.STRING) @Column(name="estado_pago") RepuestoPagoState estadoPago=RepuestoPagoState.NO_PAGADO; BigDecimal total=BigDecimal.ZERO; String proveedor; @Column(columnDefinition="text") String observaciones; @OneToMany(mappedBy="pedido",cascade=CascadeType.ALL,orphanRemoval=true) List<PedidoRepuestoItem> items=new ArrayList<>(); }
@Entity @Table(name="pedido_repuesto_item") class PedidoRepuestoItem { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pedido_repuesto_id") PedidoRepuesto pedido; String descripcion; @Enumerated(EnumType.STRING) RepuestoItemType tipo; BigDecimal cantidad; BigDecimal precio; BigDecimal subtotal; @Enumerated(EnumType.STRING) RepuestoItemState estado=RepuestoItemState.PENDIENTE_DE_PEDIR; @Column(columnDefinition="text") String observaciones; }
@Entity @Table(name="control_entrega_catalogo") class ControlEntrega extends BaseEntity { String nombre; @Column(columnDefinition="text") String descripcion; boolean obligatorio=true; int orden; boolean activo=true; }
@Entity @Table(name="revision_entrega") class RevisionEntrega extends BaseEntity { @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="ficha_id",nullable=false,unique=true) Pedido ficha; @Enumerated(EnumType.STRING) RevisionState estado=RevisionState.ABIERTA; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="aprobado_por") AppUser aprobadoPor; @Column(name="aprobado_at") Instant aprobadoAt; boolean forzada; @Column(columnDefinition="text") String observacion; @OneToMany(mappedBy="revision",cascade=CascadeType.ALL,orphanRemoval=true) List<RevisionControl> controles=new ArrayList<>(); }
@Entity @Table(name="revision_entrega_item") class RevisionControl { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="revision_entrega_id") RevisionEntrega revision; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="control_id",nullable=false) ControlEntrega control; @Enumerated(EnumType.STRING) RevisionControlState estado=RevisionControlState.PENDIENTE; @Column(columnDefinition="text") String observacion; @Column(name="correccion_necesaria",columnDefinition="text") String correccionNecesaria; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="revisado_por") AppUser revisadoPor; @Column(name="revisado_at") Instant revisadoAt; }
@Entity @Table(name="refresh_token") class RefreshToken { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="user_id") AppUser user; @Column(name="token_hash") String tokenHash; @Column(name="expires_at") Instant expiresAt; @Column(name="revoked_at") Instant revokedAt; @Column(name="created_at") Instant createdAt=Instant.now(); }
@Entity @Table(name="auditoria") class Auditoria { @Id UUID id=UUID.randomUUID(); Instant fecha=Instant.now(); @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="usuario_id") AppUser usuario; String modulo; String accion; @Column(columnDefinition="text") String descripcion; }
