package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

class OperationalIntegrityTest {
  private final DataRepository db = mock(DataRepository.class);
  private final ApiService api = new ApiService(db, mock(PasswordEncoder.class));

  @Test
  void fichaRequiresTheCurrentMotorcycleOwner() {
    UUID clienteId = UUID.randomUUID(), motoId = UUID.randomUUID();
    Cliente cliente = cliente(clienteId); Motovehiculo moto = moto(motoId);
    PropietarioMoto owner = new PropietarioMoto(); owner.cliente = cliente(UUID.randomUUID()); owner.motovehiculo = moto;
    when(db.get(Cliente.class, clienteId)).thenReturn(cliente);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);

    assertThrows(BusinessException.class, () -> api.createFicha(ficha(clienteId, motoId)));
  }

  @Test
  void intakeAssignsTheMotorcycleToOneSection() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId); moto.ingresada = false; moto.seccion = null; moto.estadoOperativo = MotoState.DISPONIBLE;
    PropietarioMoto owner = new PropietarioMoto(); owner.motovehiculo = moto; owner.cliente = cliente(UUID.randomUUID());
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);

    ApiDtos.MotorcycleResponse response = api.ingresarMoto(motoId, new ApiDtos.IntakeRequest("VENTA"));

    assertTrue(moto.ingresada);
    assertEquals(MotoSection.VENTA, moto.seccion);
    assertEquals(MotoState.EN_VENTA.label(), response.estado());
  }

  @Test
  void deliveryRequiresAFinishedFicha() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    Cliente c = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.motovehiculo = moto; ficha.cliente = c; ficha.estado = FichaState.EN_PROCESO;
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    assertThrows(BusinessException.class, () -> api.entregarFicha(ficha.id));
  }

  @Test
  void deliveringAnUnpaidFinishedFichaReleasesTheMotorcycle() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId); moto.estadoOperativo = MotoState.TERMINADA;
    Cliente c = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.motovehiculo = moto; ficha.cliente = c; ficha.estado = FichaState.TERMINADA; ficha.estadoPago = PagoState.NO_PAGADO; ficha.total = BigDecimal.valueOf(100);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    api.entregarFicha(ficha.id);

    assertEquals(FichaState.ENTREGADA, ficha.estado);
    assertEquals(PagoState.NO_PAGADO, ficha.estadoPago);
    assertFalse(moto.ingresada);
    assertEquals(MotoState.ENTREGADA, moto.estadoOperativo);
  }

  @Test
  void soldMotorcycleIsTerminalForIntake() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId); moto.ingresada = false; moto.estadoOperativo = MotoState.VENDIDA;
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);

    assertThrows(BusinessException.class, () -> api.ingresarMoto(motoId, new ApiDtos.IntakeRequest("TALLER")));
  }

  @Test
  void serviceRejectsFutureDates() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);

    assertThrows(BusinessException.class, () -> api.addService(motoId, new ApiDtos.ServiceRequest(null, 100, LocalDate.now().plusDays(1), null)));
  }

  @Test
  void cancellingFichaReleasesTheMotorcycle() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    Cliente c = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.motovehiculo = moto; ficha.cliente = c; ficha.estado = FichaState.EN_PROCESO;
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    api.fichaState(ficha.id, new ApiDtos.StateRequest("Cancelada"));

    assertFalse(moto.ingresada);
    assertEquals(MotoState.ENTREGADA, moto.estadoOperativo);
  }

  @Test
  void completingTheLastWorkKeepsTheFichaInProcessUntilReviewIsSent() {
    Motovehiculo moto = moto(UUID.randomUUID()); moto.estadoOperativo = MotoState.EN_PROCESO;
    Cliente cliente = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.motovehiculo = moto; ficha.cliente = cliente; ficha.estado = FichaState.EN_PROCESO;
    FichaTrabajo trabajo = new FichaTrabajo(); trabajo.id = UUID.randomUUID(); trabajo.ficha = ficha; trabajo.descripcion = "Cambio de aceite"; ficha.trabajos.add(trabajo);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    api.trabajoState(ficha.id, trabajo.id, new ApiDtos.StateRequest("Realizado"));

    assertEquals(FichaState.EN_PROCESO, ficha.estado);
    assertEquals(MotoState.EN_PROCESO, moto.estadoOperativo);
    api.fichaState(ficha.id, new ApiDtos.StateRequest("En revisión"));
    assertEquals(FichaState.REVISION, ficha.estado);
    assertEquals(MotoState.REVISION, moto.estadoOperativo);
  }

  @Test
  void reviewCannotStartUntilEveryWorkIsFinalized() {
    Motovehiculo moto = moto(UUID.randomUUID()); moto.estadoOperativo = MotoState.EN_PROCESO;
    Cliente cliente = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.motovehiculo = moto; ficha.cliente = cliente; ficha.estado = FichaState.EN_PROCESO;
    FichaTrabajo trabajo = new FichaTrabajo(); trabajo.id = UUID.randomUUID(); trabajo.ficha = ficha; trabajo.descripcion = "Frenos"; ficha.trabajos.add(trabajo);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    BusinessException error = assertThrows(BusinessException.class, () -> api.fichaState(ficha.id, new ApiDtos.StateRequest("En revisión")));
    assertEquals(422, error.status);
    assertEquals("Completá todos los trabajos pendientes antes de enviar la ficha a revisión", error.getMessage());
    assertEquals(FichaState.EN_PROCESO, ficha.estado);
    assertEquals(MotoState.EN_PROCESO, moto.estadoOperativo);
  }

  @Test
  void fichaPaymentAccumulatesExactAmountsAndExposesBalance() {
    Ficha ficha = fichaParaPago("500000.00");
    when(db.getForUpdate(Ficha.class, ficha.id)).thenReturn(ficha);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    ApiDtos.PagoResponse first = api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("200000.00"), LocalDate.now().minusDays(1), "Transferencia"));
    ApiDtos.PagoResponse second = api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("300000.00"), LocalDate.now(), "Efectivo"));
    ApiDtos.FichaResponse response = api.ficha(ficha.id);

    assertEquals(new BigDecimal("200000.00"), first.monto());
    assertEquals("Transferencia", first.medioPago());
    assertEquals(new BigDecimal("300000.00"), second.monto());
    assertEquals(PagoState.PAGADO, ficha.estadoPago);
    assertEquals(new BigDecimal("500000.00"), response.montoCobrado());
    assertEquals(BigDecimal.ZERO.setScale(2), response.saldoPendiente());
  }

  @Test
  void fichaPaymentRejectsExcessAndAnnullingItRestoresTheBalance() {
    Ficha ficha = fichaParaPago("500000.00");
    when(db.getForUpdate(Ficha.class, ficha.id)).thenReturn(ficha);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    ApiDtos.PagoResponse payment = api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("200000.00"), null, null));
    assertThrows(BusinessException.class, () -> api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("300000.01"), null, null)));
    ApiDtos.PagoResponse annulled = api.anularFichaPago(ficha.id, payment.id());
    ApiDtos.FichaResponse response = api.ficha(ficha.id);

    assertTrue(annulled.anulado());
    assertEquals(PagoState.NO_PAGADO, ficha.estadoPago);
    assertEquals(BigDecimal.ZERO.setScale(2), response.montoCobrado());
    assertEquals(new BigDecimal("500000.00"), response.saldoPendiente());
  }

  @Test
  void repuestoPaymentUsesItsOwnExactBalance() {
    RepuestoPedido pedido = repuestoParaPago("500000.00");
    when(db.getForUpdate(RepuestoPedido.class, pedido.id)).thenReturn(pedido);
    when(db.get(RepuestoPedido.class, pedido.id)).thenReturn(pedido);

    api.registrarRepuestoPago(pedido.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("200000.00"), LocalDate.now(), "Mercado Pago"));
    ApiDtos.RepuestoResponse response = api.repuesto(pedido.id);

    assertEquals(PagoState.PARCIAL, pedido.estadoPago);
    assertEquals(new BigDecimal("200000.00"), response.montoCobrado());
    assertEquals(new BigDecimal("300000.00"), response.saldoPendiente());
  }

  @Test
  void paidFichaCannotBeCancelledAndPaymentsCannotUseFutureDates() {
    Ficha ficha = fichaParaPago("100.00"); ficha.estado = FichaState.EN_PROCESO;
    when(db.getForUpdate(Ficha.class, ficha.id)).thenReturn(ficha);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("50.00"), null, null));

    assertThrows(BusinessException.class, () -> api.fichaState(ficha.id, new ApiDtos.StateRequest("Cancelada")));
    assertThrows(BusinessException.class, () -> api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(BigDecimal.ZERO, null, null)));
    assertThrows(BusinessException.class, () -> api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("10.00"), LocalDate.now().plusDays(1), null)));
  }

  @Test
  void paymentStatusIsPartialAfterOneOfSeveralReceipts() {
    Ficha ficha = fichaParaPago("500000.00");
    when(db.getForUpdate(Ficha.class, ficha.id)).thenReturn(ficha);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    api.registrarFichaPago(ficha.id, new ApiDtos.PagoRegistroRequest(new BigDecimal("200000.00"), null, "Débito"));
    ApiDtos.FichaResponse response = api.ficha(ficha.id);

    assertEquals(PagoState.PARCIAL, ficha.estadoPago);
    assertEquals("Parcial", response.estadoPago());
  }

  @Test
  void lastActiveAdminCannotBeDeleted() {
    AppUser admin = new AppUser(); admin.rol = Role.ADMINISTRACION; admin.activo = true;
    when(db.get(AppUser.class, admin.id)).thenReturn(admin);
    when(db.count(anyString(), anyMap())).thenReturn(1L);

    assertThrows(BusinessException.class, () -> api.deleteUser(admin.id));
  }

  @Test
  void motorcycleOwnerFilterUsesCurrentPeriod() {
    UUID motoId = UUID.randomUUID(), clientId = UUID.randomUUID();
    api.motorcycles(null, clientId, null, null, null, false, 0, 10, "patente", "ASC");
    verify(db).list(contains("fechaHasta is null"), eq(Object.class), anyMap(), eq(0), eq(10));
  }

  @Test
  void motorcycleStateFilterDoesNotReadFichaHistory() {
    api.motorcycles(null, null, null, "En proceso", null, false, 0, 10, "patente", "ASC");
    verify(db, never()).all(contains("from Ficha"), eq(Ficha.class), anyMap());
    verify(db).list(contains("e.estadoOperativo=:state"), eq(Object.class), anyMap(), eq(0), eq(10));
  }

  @Test
  void resetScriptIsExplicitAndDoesNotCascade() throws Exception {
    String script = java.nio.file.Files.readString(java.nio.file.Path.of("ops/reset-operational-data.sh"));
    assertTrue(script.contains("RESET_OPERATIONAL_DATA=YES"));
    assertTrue(script.contains("DELETE FROM pago;"));
    assertFalse(script.toUpperCase(Locale.ROOT).contains("CASCADE"));
  }

  @Test
  void ownershipMigrationUsesHalfOpenPeriodsForSameDaySales() throws Exception {
    String migration = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V22__venta_transferencia_cancelable.sql"));
    assertTrue(migration.contains("daterange(fecha_desde, COALESCE(fecha_hasta, 'infinity'::date), '[)')"));
    assertTrue(migration.contains("SET fecha_hasta = fecha_hasta + 1"));
  }

  @Test
  void transferIntegrityMigrationUsesCanonicalWorkflowState() throws Exception {
    String migration = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V24__transferencia_integridad_canonica.sql"));
    assertTrue(migration.contains("ALTER COLUMN fecha_transferencia DROP NOT NULL"));
    assertTrue(migration.contains("TRANSFERENCIA_EN_PROCESO"));
    assertFalse(migration.contains("TRANSFERENCIA_EN_CURSO"));
  }

  @Test
  void fichaCannotBeOpenedWhenTheMotorcycleAlreadyHasOne() {
    UUID clienteId = UUID.randomUUID(), motoId = UUID.randomUUID();
    Cliente cliente = cliente(clienteId); Motovehiculo moto = moto(motoId);
    PropietarioMoto owner = new PropietarioMoto(); owner.cliente = cliente; owner.motovehiculo = moto;
    when(db.get(Cliente.class, clienteId)).thenReturn(cliente);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);
    when(db.count(contains("from Ficha"), anyMap())).thenReturn(1L);

    assertThrows(BusinessException.class, () -> api.createFicha(ficha(clienteId, motoId)));
  }

  @Test
  void fichaRequiresAtLeastOneWork() {
    UUID clienteId = UUID.randomUUID(), motoId = UUID.randomUUID();
    Cliente cliente = cliente(clienteId); Motovehiculo moto = moto(motoId);
    PropietarioMoto owner = new PropietarioMoto(); owner.cliente = cliente; owner.motovehiculo = moto;
    when(db.get(Cliente.class, clienteId)).thenReturn(cliente);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);
    when(db.count(contains("from Ficha"), anyMap())).thenReturn(0L);

    assertThrows(BusinessException.class, () -> api.createFicha(ficha(clienteId, motoId)));
  }

  @Test
  void fichaSummaryOnlyReturnsLinkedRepuestoOrders() {
    UUID fichaId = UUID.randomUUID();
    Ficha ficha = new Ficha(); ficha.id = fichaId;
    Motovehiculo moto = moto(UUID.randomUUID());
    Cliente cliente = cliente(UUID.randomUUID());
    RepuestoPedido pedido = new RepuestoPedido(); pedido.id = UUID.randomUUID(); pedido.numero = "R-1"; pedido.ficha = ficha; pedido.motovehiculo = moto; pedido.cliente = cliente;
    when(db.get(Ficha.class, fichaId)).thenReturn(ficha);
    when(db.all(contains("e.ficha.id=:ficha"), eq(RepuestoPedido.class), anyMap())).thenReturn(List.of(pedido));

    List<ApiDtos.RepuestoResponse> response = api.repuestosFicha(fichaId);

    assertEquals(1, response.size());
    assertEquals(fichaId, response.getFirst().fichaId());
    verify(db).all(contains("e.ficha.id=:ficha"), eq(RepuestoPedido.class), anyMap());
  }

  @Test
  void cancellingARepuestoItemRecalculatesThePedidoTotal() {
    Motovehiculo moto = moto(UUID.randomUUID());
    Cliente cliente = cliente(UUID.randomUUID());
    RepuestoPedido pedido = new RepuestoPedido(); pedido.id = UUID.randomUUID(); pedido.numero = "R-1"; pedido.motovehiculo = moto; pedido.cliente = cliente; pedido.total = BigDecimal.valueOf(150);
    RepuestoPedidoItem retained = new RepuestoPedidoItem(); retained.id = UUID.randomUUID(); retained.pedido = pedido; retained.descripcion = "Aceite"; retained.tipo = RepuestoCategoria.REPUESTO; retained.subtotal = BigDecimal.valueOf(100); retained.estado = RepuestoItemState.PEDIDO;
    RepuestoPedidoItem cancelled = new RepuestoPedidoItem(); cancelled.id = UUID.randomUUID(); cancelled.pedido = pedido; cancelled.descripcion = "Filtro"; cancelled.tipo = RepuestoCategoria.REPUESTO; cancelled.subtotal = BigDecimal.valueOf(50); cancelled.estado = RepuestoItemState.PEDIDO;
    pedido.items.add(retained); pedido.items.add(cancelled);
    when(db.get(RepuestoPedido.class, pedido.id)).thenReturn(pedido);

    ApiDtos.RepuestoResponse response = api.repuestoItemEstado(pedido.id, cancelled.id, new ApiDtos.StateRequest("Cancelado"));

    assertEquals(RepuestoItemState.CANCELADO, cancelled.estado);
    assertEquals(new BigDecimal("100.00"), pedido.total);
    assertEquals(new BigDecimal("100.00"), response.total());
  }

  @Test
  void fichaPdfIncludesTheLinkedRepuestoSummary() throws Exception {
    UUID fichaId = UUID.randomUUID();
    Motovehiculo moto = moto(UUID.randomUUID()); moto.patente = "AA123AA"; moto.modelo = "Wave";
    Cliente cliente = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = fichaId; ficha.numero = "F-1"; ficha.cliente = cliente; ficha.motovehiculo = moto; ficha.fechaIngreso = LocalDate.now(); ficha.total = BigDecimal.valueOf(100); ficha.descuentoGlobal = BigDecimal.ZERO;
    FichaTrabajo trabajo = new FichaTrabajo(); trabajo.descripcion = "Cambio de aceite"; trabajo.precioAplicado = BigDecimal.valueOf(100); trabajo.descuento = BigDecimal.ZERO; trabajo.subtotal = BigDecimal.valueOf(100); ficha.trabajos.add(trabajo);
    RepuestoPedido pedido = new RepuestoPedido(); pedido.id = UUID.randomUUID(); pedido.numero = "R-1"; pedido.ficha = ficha; pedido.motovehiculo = moto; pedido.cliente = cliente; pedido.total = BigDecimal.valueOf(50);
    RepuestoPedidoItem item = new RepuestoPedidoItem(); item.descripcion = "Filtro"; item.tipo = RepuestoCategoria.REPUESTO; item.cantidad = BigDecimal.ONE; item.precio = BigDecimal.valueOf(50); item.subtotal = BigDecimal.valueOf(50); pedido.items.add(item);
    when(db.get(Ficha.class, fichaId)).thenReturn(ficha);
    when(db.all(contains("e.ficha.id=:ficha"), eq(RepuestoPedido.class), anyMap())).thenReturn(List.of(pedido));

    ResponseEntity<byte[]> response = new ApiController(api, mock(AuthService.class)).pdf(fichaId);

    assertEquals("application/pdf", response.getHeaders().getContentType().toString());
    assertTrue(response.getBody() != null && response.getBody().length > 100);
    assertEquals("%PDF", new String(response.getBody(), 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
    com.lowagie.text.pdf.PdfReader pdf = new com.lowagie.text.pdf.PdfReader(response.getBody());
    String content = new com.lowagie.text.pdf.parser.PdfTextExtractor(pdf).getTextFromPage(1);
    pdf.close();
    assertTrue(content.contains("R-1"));
    assertTrue(content.contains("Filtro"));
    assertTrue(content.contains("TOTAL PRESUPUESTO"));
  }

  @Test
  void serviceCannotReferenceAnotherMotorcycleFicha() {
    UUID motoId = UUID.randomUUID(), fichaId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId); Ficha ficha = new Ficha(); ficha.motovehiculo = moto(UUID.randomUUID());
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.get(Ficha.class, fichaId)).thenReturn(ficha);

    assertThrows(BusinessException.class, () -> api.addService(motoId, new ApiDtos.ServiceRequest(fichaId, 100, null, null)));
  }

  @Test
  void deletingCatalogWorkMakesItUnavailableForFutureSuggestions() {
    UUID trabajoId = UUID.randomUUID();
    TrabajoCatalogo trabajo = new TrabajoCatalogo();
    trabajo.id = trabajoId;
    trabajo.activo = true;
    trabajo.descripcion = "Cambio de aceite";
    when(db.get(TrabajoCatalogo.class, trabajoId)).thenReturn(trabajo);

    api.deleteTrabajoCatalogo(trabajoId);

    assertFalse(trabajo.activo);
  }

  @Test
  void directTransferCreationIsBlocked() {
    assertThrows(BusinessException.class, () -> api.createTransfer(new ApiDtos.TransferRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), "Venta")));
    verify(db, never()).persist(any(PropietarioMoto.class));
  }

  @Test
  void transferRejectsTheCurrentClientAndFutureDates() {
    UUID motoId = UUID.randomUUID(), clientId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId); Cliente client = cliente(clientId);
    PropietarioMoto current = new PropietarioMoto(); current.motovehiculo = moto; current.cliente = client; current.fechaDesde = LocalDate.now().minusDays(5);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.get(Cliente.class, clientId)).thenReturn(client);
    Cliente futureClient = cliente(UUID.randomUUID());
    when(db.get(eq(Cliente.class), any(UUID.class))).thenReturn(futureClient);
    when(db.get(Cliente.class, clientId)).thenReturn(client);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(current);

    assertThrows(BusinessException.class, () -> api.createTransfer(new ApiDtos.TransferRequest(motoId, clientId, LocalDate.now(), null)));
    assertThrows(BusinessException.class, () -> api.createTransfer(new ApiDtos.TransferRequest(motoId, UUID.randomUUID(), LocalDate.now().plusDays(1), null)));
  }

  @Test
  void directTransferUpdatesAreBlocked() {
    assertThrows(BusinessException.class, () -> api.updateTransfer(UUID.randomUUID(), new ApiDtos.TransferUpdateRequest(UUID.randomUUID(), LocalDate.now(), "Fecha corregida")));
    verify(db, never()).persist(any(PropietarioMoto.class));
  }

  @Test
  void directTransferDeletesAreBlocked() {
    assertThrows(BusinessException.class, () -> api.deleteTransfer(UUID.randomUUID()));
    verify(db, never()).persist(any(PropietarioMoto.class));
  }

  @Test
  void clientAutocompleteSearchesPhoneAndReturnsAllMatchingClients() {
    List<Cliente> clients = new ArrayList<>();
    for (int index = 0; index < 16; index++) {
      Cliente client = cliente(UUID.randomUUID()); client.nombre = "Cliente " + index; client.telefono = "341555" + index; clients.add(client);
    }
    when(db.all(contains("telefono"), eq(Cliente.class), anyMap())).thenReturn(clients);

    List<ApiDtos.AutocompleteResponse> result = api.clientAutocomplete("341");

    assertEquals(16, result.size());
    verify(db).all(contains("lower(coalesce(e.telefono,''))"), eq(Cliente.class), anyMap());
  }

  private static ApiDtos.FichaRequest ficha(UUID clienteId, UUID motoId) {
    return new ApiDtos.FichaRequest(clienteId, motoId, null, null, null, null, null, BigDecimal.ZERO, false, List.of());
  }
  private static Ficha fichaConTrabajos(TrabajoState... estados) {
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.cliente = cliente(UUID.randomUUID()); ficha.motovehiculo = moto(UUID.randomUUID()); ficha.descuentoGlobal = BigDecimal.ZERO;
    for (int index = 0; index < estados.length; index++) {
      FichaTrabajo trabajo = new FichaTrabajo(); trabajo.id = UUID.randomUUID(); trabajo.ficha = ficha; trabajo.descripcion = "Trabajo " + index; trabajo.estadoTrabajo = estados[index];
      ficha.trabajos.add(trabajo);
    }
    return ficha;
  }
  private static Ficha fichaParaPago(String total) { Ficha ficha = fichaConTrabajos(TrabajoState.PENDIENTE); ficha.total = new BigDecimal(total); return ficha; }
  private static RepuestoPedido repuestoParaPago(String total) { RepuestoPedido pedido = new RepuestoPedido(); pedido.id = UUID.randomUUID(); pedido.numero = "R-1"; pedido.motovehiculo = moto(UUID.randomUUID()); pedido.cliente = cliente(UUID.randomUUID()); pedido.total = new BigDecimal(total); return pedido; }
  private static Cliente cliente(UUID id) { Cliente c = new Cliente(); c.id = id; c.nombre = "Cliente"; return c; }
  private static Motovehiculo moto(UUID id) { Motovehiculo m = new Motovehiculo(); m.id = id; m.activo = true; m.seccion = MotoSection.TALLER; m.ingresada = true; m.estadoOperativo = MotoState.INGRESADA_TALLER; m.marca = new MarcaMoto(); m.marca.id = UUID.randomUUID(); m.marca.nombre = "Honda"; return m; }
}
