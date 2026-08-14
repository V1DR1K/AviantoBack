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
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);

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
  void deliveringAFinishedFichaReleasesTheMotorcycle() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId); moto.estadoOperativo = MotoState.TERMINADA;
    Cliente c = cliente(UUID.randomUUID());
    Ficha ficha = new Ficha(); ficha.id = UUID.randomUUID(); ficha.numero = "F-1"; ficha.motovehiculo = moto; ficha.cliente = c; ficha.estado = FichaState.TERMINADA;
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);

    api.entregarFicha(ficha.id);

    assertEquals(FichaState.ENTREGADA, ficha.estado);
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

    assertThrows(BusinessException.class, () -> api.fichaState(ficha.id, new ApiDtos.StateRequest("En revisión")));
    assertEquals(FichaState.EN_PROCESO, ficha.estado);
    assertEquals(MotoState.EN_PROCESO, moto.estadoOperativo);
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
    assertFalse(script.toUpperCase(Locale.ROOT).contains("CASCADE"));
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
  void transferClosesTheCurrentPeriodAndCreatesTheNewOwner() {
    UUID motoId = UUID.randomUUID(), oldId = UUID.randomUUID(), newId = UUID.randomUUID();
    LocalDate transferDate = LocalDate.now().minusDays(1);
    Motovehiculo moto = moto(motoId); moto.seccion = MotoSection.VENTA; moto.estadoOperativo = MotoState.EN_VENTA; moto.ingresada = true; moto.patente = "AA123AA"; moto.modelo = "FZ"; MarcaMoto brand = new MarcaMoto(); brand.nombre = "Yamaha"; moto.marca = brand;
    Cliente oldClient = cliente(oldId); Cliente newClient = cliente(newId); newClient.nombre = "Nuevo cliente";
    PropietarioMoto current = new PropietarioMoto(); current.motovehiculo = moto; current.cliente = oldClient; current.fechaDesde = transferDate.minusDays(10);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.get(Cliente.class, newId)).thenReturn(newClient);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(current);

    ApiDtos.TransferResponse response = api.createTransfer(new ApiDtos.TransferRequest(motoId, newId, transferDate, "Venta"));

    assertEquals(newId, response.clienteNuevoId());
    assertEquals(transferDate.minusDays(1), current.fechaHasta);
    verify(db).flush();
    verify(db).persist(any(PropietarioMoto.class));
    verify(db).persist(any(TransferenciaMoto.class));
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
  void editingTransferRebuildsTheOwnerPeriods() {
    UUID motoId = UUID.randomUUID(), transferId = UUID.randomUUID(), oldId = UUID.randomUUID(), newId = UUID.randomUUID();
    LocalDate initialDate = LocalDate.now().minusDays(10), oldTransferDate = LocalDate.now().minusDays(2), newTransferDate = LocalDate.now().minusDays(3);
    Motovehiculo moto = moto(motoId); moto.patente = "AA123AA"; MarcaMoto brand = new MarcaMoto(); brand.nombre = "Yamaha"; moto.marca = brand; Cliente oldClient = cliente(oldId); Cliente newClient = cliente(newId); newClient.nombre = "Nuevo cliente";
    PropietarioMoto initial = new PropietarioMoto(); initial.motovehiculo = moto; initial.cliente = oldClient; initial.fechaDesde = initialDate; initial.fechaHasta = oldTransferDate.minusDays(1);
    PropietarioMoto current = new PropietarioMoto(); current.motovehiculo = moto; current.cliente = newClient; current.fechaDesde = oldTransferDate;
    TransferenciaMoto transfer = new TransferenciaMoto(); transfer.id = transferId; transfer.motovehiculo = moto; transfer.clienteAnterior = oldClient; transfer.clienteNuevo = newClient; transfer.fechaTransferencia = oldTransferDate;
    when(db.get(TransferenciaMoto.class, transferId)).thenReturn(transfer);
    when(db.get(Cliente.class, newId)).thenReturn(newClient);
    when(db.all(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(List.of(initial, current));
    when(db.all(contains("from TransferenciaMoto"), eq(TransferenciaMoto.class), anyMap())).thenReturn(List.of(transfer));

    api.updateTransfer(transferId, new ApiDtos.TransferUpdateRequest(newId, newTransferDate, "Fecha corregida"));

    assertEquals(newTransferDate, transfer.fechaTransferencia);
    assertEquals(newTransferDate.minusDays(1), initial.fechaHasta);
    assertNotNull(current.deletedAt);
    verify(db).flush();
    verify(db).persist(any(PropietarioMoto.class));
  }

  @Test
  void deletingTransferReactivatesThePreviousOwnerPeriodWithoutPhysicalDeletion() {
    UUID motoId = UUID.randomUUID(), transferId = UUID.randomUUID(), oldId = UUID.randomUUID(), newId = UUID.randomUUID();
    LocalDate initialDate = LocalDate.now().minusDays(10), transferDate = LocalDate.now().minusDays(2);
    Motovehiculo moto = moto(motoId); moto.patente = "AA123AA"; Cliente oldClient = cliente(oldId); Cliente newClient = cliente(newId);
    PropietarioMoto initial = new PropietarioMoto(); initial.motovehiculo = moto; initial.cliente = oldClient; initial.fechaDesde = initialDate; initial.fechaHasta = transferDate.minusDays(1);
    PropietarioMoto current = new PropietarioMoto(); current.motovehiculo = moto; current.cliente = newClient; current.fechaDesde = transferDate;
    TransferenciaMoto transfer = new TransferenciaMoto(); transfer.id = transferId; transfer.motovehiculo = moto; transfer.clienteAnterior = oldClient; transfer.clienteNuevo = newClient; transfer.fechaTransferencia = transferDate;
    when(db.get(TransferenciaMoto.class, transferId)).thenReturn(transfer);
    when(db.all(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(List.of(initial, current));
    when(db.all(contains("from TransferenciaMoto"), eq(TransferenciaMoto.class), anyMap())).thenReturn(List.of());

    api.deleteTransfer(transferId);

    assertNotNull(transfer.deletedAt);
    assertNotNull(current.deletedAt);
    assertNull(initial.fechaHasta);
    verify(db).flush();
  }

  private static ApiDtos.FichaRequest ficha(UUID clienteId, UUID motoId) {
    return new ApiDtos.FichaRequest(clienteId, motoId, null, null, null, null, null, BigDecimal.ZERO, false, List.of());
  }
  private static Cliente cliente(UUID id) { Cliente c = new Cliente(); c.id = id; c.nombre = "Cliente"; return c; }
  private static Motovehiculo moto(UUID id) { Motovehiculo m = new Motovehiculo(); m.id = id; m.activo = true; m.seccion = MotoSection.TALLER; m.ingresada = true; m.estadoOperativo = MotoState.INGRESADA_TALLER; m.marca = new MarcaMoto(); m.marca.id = UUID.randomUUID(); m.marca.nombre = "Honda"; return m; }
}
