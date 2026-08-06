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
enum OrderState { EN_PROCESO, APROBADO, PAGADO, CANCELADO;
  static OrderState of(String v) { return switch (v) { case "En proceso" -> EN_PROCESO; case "Aprobado" -> APROBADO; case "Pagado" -> PAGADO; case "Cancelado" -> CANCELADO; default -> throw new BusinessException(422, "Estado inválido"); }; }
  String label() { return switch(this) { case EN_PROCESO -> "En proceso"; case APROBADO -> "Aprobado"; case PAGADO -> "Pagado"; case CANCELADO -> "Cancelado"; }; }
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
@Entity class Motovehiculo extends BaseEntity { @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="cliente_id",nullable=false) Cliente cliente; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="marca_id",nullable=false) MarcaMoto marca; String modelo; String patente; Integer anio; Integer kilometraje; @Column(columnDefinition="text") String observaciones; boolean activo=true; }
@Entity @Table(name="item_catalogo") class ItemCatalogo extends BaseEntity { String descripcion; @Enumerated(EnumType.STRING) ItemType tipo; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="categoria_id",nullable=false) CategoriaCatalogo categoria; @Column(name="precio_base") BigDecimal precioBase; @Column(columnDefinition="text") String observaciones; boolean activo=true; }
@Entity @Table(name="price_history") class PriceHistory { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="item_id") ItemCatalogo item; @Column(name="precio_anterior") BigDecimal precioAnterior; @Column(name="precio_nuevo") BigDecimal precioNuevo; @Column(name="changed_at") Instant changedAt=Instant.now(); @Column(name="changed_by") UUID changedBy; }
@Entity class Pedido extends BaseEntity { @Column(unique=true) String numero; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="cliente_id",nullable=false) Cliente cliente; @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="motovehiculo_id",nullable=false) Motovehiculo motovehiculo; @Enumerated(EnumType.STRING) DocumentType documento; LocalDate vencimiento; @Column(columnDefinition="text") String observaciones; @Column(name="descuento_global") BigDecimal descuentoGlobal=BigDecimal.ZERO; boolean iva; @Enumerated(EnumType.STRING) OrderState estado=OrderState.EN_PROCESO; BigDecimal total=BigDecimal.ZERO; @OneToMany(mappedBy="pedido",cascade=CascadeType.ALL,orphanRemoval=true) List<PedidoItem> items=new ArrayList<>(); @OneToMany(mappedBy="pedido",cascade=CascadeType.ALL,orphanRemoval=true) List<PedidoFoto> fotos=new ArrayList<>(); }
@Entity @Table(name="pedido_item") class PedidoItem { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pedido_id") Pedido pedido; @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="item_catalogo_id") ItemCatalogo itemCatalogo; String descripcion; @Enumerated(EnumType.STRING) ItemType tipo; BigDecimal cantidad; @Column(name="precio_unitario") BigDecimal precioUnitario; BigDecimal descuento; BigDecimal subtotal; }
@Entity @Table(name="pedido_foto") class PedidoFoto { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pedido_id") Pedido pedido; String filename; @Column(name="content_type") String contentType; @JdbcTypeCode(Types.VARBINARY) byte[] content; @Column(name="created_at") Instant createdAt=Instant.now(); }
@Entity @Table(name="refresh_token") class RefreshToken { @Id UUID id=UUID.randomUUID(); @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="user_id") AppUser user; @Column(name="token_hash") String tokenHash; @Column(name="expires_at") Instant expiresAt; @Column(name="revoked_at") Instant revokedAt; @Column(name="created_at") Instant createdAt=Instant.now(); }
@Entity @Table(name="auditoria") class Auditoria { @Id UUID id=UUID.randomUUID(); Instant fecha=Instant.now(); @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="usuario_id") AppUser usuario; String modulo; String accion; @Column(columnDefinition="text") String descripcion; }
