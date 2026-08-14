package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;

class SaleWorkflowTest {
  private final DataRepository db = mock(DataRepository.class);
  private final ApiService api = new ApiService(db, mock(PasswordEncoder.class));

  @Test
  void enteringVentaCreatesANumberedFichaAndSnapshotsActiveTemplateItems() {
    Motovehiculo moto = moto(); moto.ingresada = false; moto.seccion = null; moto.estadoOperativo = MotoState.DISPONIBLE;
    Cliente seller = cliente("Vendedor");
    PropietarioMoto owner = owner(moto, seller);
    VentaChecklistPlantilla template = new VentaChecklistPlantilla(); template.etiqueta = "Formulario firmado"; template.orden = 4; template.obligatorio = true;
    when(db.get(Motovehiculo.class, moto.id)).thenReturn(moto);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);
    when(db.count(contains("from VentaFicha"), anyMap())).thenReturn(0L);
    when(db.nextVal("ficha_venta_numero_seq")).thenReturn(7L);
    when(db.all(contains("VentaChecklistPlantilla"), eq(VentaChecklistPlantilla.class), anyMap())).thenReturn(List.of(template));

    api.ingresarMoto(moto.id, new ApiDtos.IntakeRequest("VENTA"));

    var ficha = org.mockito.ArgumentCaptor.forClass(VentaFicha.class);
    verify(db).persist(ficha.capture());
    verify(db).persist(isA(VentaFichaItem.class));
    assertEquals("V-7", ficha.getValue().numero);
    assertEquals(seller, ficha.getValue().vendedor);
    assertEquals(1, ficha.getValue().items.size());
    assertEquals("Formulario firmado", ficha.getValue().items.getFirst().etiqueta);
    assertEquals(VentaChecklistState.PENDIENTE, ficha.getValue().items.getFirst().estado);
    assertEquals(MotoState.EN_VENTA, moto.estadoOperativo);
  }

  @Test
  void buyerAndChecklistAreRequiredBeforeTransferAndOwnershipStaysWithSeller() {
    Motovehiculo moto = moto(); moto.seccion = MotoSection.VENTA; moto.ingresada = true; moto.estadoOperativo = MotoState.EN_VENTA;
    Cliente seller = cliente("Vendedor"), buyer = cliente("Comprador");
    VentaFicha sale = sale(moto, seller, null, true);
    PropietarioMoto owner = owner(moto, seller);
    when(db.getForUpdate(VentaFicha.class, sale.id)).thenReturn(sale);
    when(db.get(Cliente.class, buyer.id)).thenReturn(buyer);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);

    assertThrows(BusinessException.class, () -> api.iniciarTransferenciaVenta(sale.id));
    api.updateVentaComprador(sale.id, new ApiDtos.VentaCompradorRequest(buyer.id));
    assertNull(owner.fechaHasta);
    sale.items.getFirst().estado = VentaChecklistState.NO_APLICA;
    assertThrows(BusinessException.class, () -> api.iniciarTransferenciaVenta(sale.id));
    sale.items.getFirst().estado = VentaChecklistState.REALIZADO;

    api.iniciarTransferenciaVenta(sale.id);

    assertEquals(MotoState.TRANSFERENCIA_EN_PROCESO, moto.estadoOperativo);
    assertNotNull(sale.transferencia);
    assertEquals(buyer, sale.transferencia.clienteNuevo);
    assertNull(owner.fechaHasta);
    verify(db, never()).persist(isA(PropietarioMoto.class));
  }

  @Test
  void cancelledTransferReturnsTheSaleToMarketAndCanBeRestarted() {
    Motovehiculo moto = moto(); moto.seccion = MotoSection.VENTA; moto.ingresada = true; moto.estadoOperativo = MotoState.TRANSFERENCIA_EN_PROCESO;
    Cliente seller = cliente("Vendedor"), firstBuyer = cliente("Primer comprador"), nextBuyer = cliente("Segundo comprador");
    VentaFicha sale = sale(moto, seller, firstBuyer, true); sale.items.getFirst().estado = VentaChecklistState.REALIZADO;
    TransferenciaMoto transfer = transferencia(sale); sale.transferencia = transfer;
    PropietarioMoto owner = owner(moto, seller);
    when(db.getForUpdate(VentaFicha.class, sale.id)).thenReturn(sale);
    when(db.get(Cliente.class, nextBuyer.id)).thenReturn(nextBuyer);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);

    api.cancelarTransferenciaVenta(sale.id);

    assertEquals(MotoState.EN_VENTA, moto.estadoOperativo);
    assertNull(sale.comprador);
    assertNotNull(transfer.canceladaAt);
    api.updateVentaComprador(sale.id, new ApiDtos.VentaCompradorRequest(nextBuyer.id));
    api.iniciarTransferenciaVenta(sale.id);
    assertSame(transfer, sale.transferencia);
    assertEquals(nextBuyer, transfer.clienteNuevo);
    assertNull(transfer.canceladaAt);
    assertEquals(MotoState.TRANSFERENCIA_EN_PROCESO, moto.estadoOperativo);
  }

  @Test
  void attendanceRequiresACompleteAppointmentThatIsNotFuture() {
    Motovehiculo moto = moto(); moto.seccion = MotoSection.VENTA; moto.ingresada = true; moto.estadoOperativo = MotoState.TRANSFERENCIA_EN_PROCESO;
    VentaFicha sale = sale(moto, cliente("Vendedor"), cliente("Comprador"), true);
    TransferenciaMoto transfer = transferencia(sale); sale.transferencia = transfer;
    when(db.getForUpdate(VentaFicha.class, sale.id)).thenReturn(sale);

    api.programarCitaVenta(sale.id, new ApiDtos.VentaCitaRequest(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1), LocalTime.NOON, "Registro"));
    assertThrows(BusinessException.class, () -> api.registrarAsistenciaVenta(sale.id));
    transfer.citaFecha = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(1);

    api.registrarAsistenciaVenta(sale.id);

    assertNotNull(transfer.asistenciaAt);
  }

  @Test
  void completionAtomicallyChangesOwnershipAndLocksTheSaleAfterwards() {
    Motovehiculo moto = moto(); moto.seccion = MotoSection.VENTA; moto.ingresada = true; moto.estadoOperativo = MotoState.TRANSFERENCIA_EN_PROCESO;
    Cliente seller = cliente("Vendedor"), buyer = cliente("Comprador");
    VentaFicha sale = sale(moto, seller, buyer, true);
    sale.items.getFirst().estado = VentaChecklistState.REALIZADO;
    TransferenciaMoto transfer = transferencia(sale); transfer.citaFecha = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(1); transfer.citaHora = LocalTime.NOON; transfer.citaLugar = "Registro"; transfer.asistenciaAt = Instant.now(); sale.transferencia = transfer;
    PropietarioMoto current = owner(moto, seller); current.fechaDesde = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    when(db.getForUpdate(VentaFicha.class, sale.id)).thenReturn(sale);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(current);

    ApiDtos.VentaFichaResponse response = api.completarFichaVenta(sale.id);

    var newOwner = org.mockito.ArgumentCaptor.forClass(PropietarioMoto.class);
    verify(db).persist(newOwner.capture());
    assertEquals(buyer, newOwner.getValue().cliente);
    assertEquals(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")), newOwner.getValue().fechaDesde);
    assertEquals(newOwner.getValue().fechaDesde, current.fechaHasta);
    assertEquals(MotoState.VENDIDA, moto.estadoOperativo);
    assertFalse(moto.ingresada);
    assertNotNull(transfer.finalizadaAt);
    assertNotNull(response.finalizadaAt());
    assertThrows(BusinessException.class, () -> api.updateVentaChecklistItem(sale.id, sale.items.getFirst().id, new ApiDtos.VentaChecklistItemRequest("Pendiente")));
    assertThrows(BusinessException.class, () -> api.updateVentaComprador(sale.id, new ApiDtos.VentaCompradorRequest(buyer.id)));
    assertThrows(BusinessException.class, () -> api.programarCitaVenta(sale.id, new ApiDtos.VentaCitaRequest(LocalDate.now(), LocalTime.NOON, "Otro")));
  }

  @Test
  void legacyCompletionEndpointUsesTheSameFinalizationGate() {
    Motovehiculo moto = moto(); moto.seccion = MotoSection.VENTA; moto.ingresada = true; moto.estadoOperativo = MotoState.TRANSFERENCIA_EN_PROCESO;
    VentaFicha sale = sale(moto, cliente("Vendedor"), cliente("Comprador"), true);
    sale.items.getFirst().estado = VentaChecklistState.REALIZADO;
    sale.transferencia = transferencia(sale);
    when(db.one(contains("from VentaFicha"), eq(VentaFicha.class), anyMap())).thenReturn(sale);
    when(db.getForUpdate(VentaFicha.class, sale.id)).thenReturn(sale);

    assertThrows(BusinessException.class, () -> api.completarVenta(moto.id));
    assertEquals(MotoState.TRANSFERENCIA_EN_PROCESO, moto.estadoOperativo);
    verify(db, never()).persist(isA(PropietarioMoto.class));
  }

  @Test
  void saleEndpointsDeclareTheirRoleBoundaries() throws Exception {
    PreAuthorize checklist = ApiController.class.getDeclaredMethod("ventaItem", UUID.class, UUID.class, ApiDtos.VentaChecklistItemRequest.class).getAnnotation(PreAuthorize.class);
    PreAuthorize buyer = ApiController.class.getDeclaredMethod("ventaComprador", UUID.class, ApiDtos.VentaCompradorRequest.class).getAnnotation(PreAuthorize.class);
    PreAuthorize cancel = ApiController.class.getDeclaredMethod("ventaCancelarTransferencia", UUID.class).getAnnotation(PreAuthorize.class);
    PreAuthorize complete = ApiController.class.getDeclaredMethod("ventaCompletar", UUID.class).getAnnotation(PreAuthorize.class);

    assertTrue(checklist.value().contains("ROLE_OPERARIO"));
    assertEquals("hasAuthority('ROLE_ADMINISTRACION')", buyer.value());
    assertEquals("hasAuthority('ROLE_ADMINISTRACION')", cancel.value());
    assertEquals("hasAuthority('ROLE_ADMINISTRACION')", complete.value());
  }

  private static VentaFicha sale(Motovehiculo moto, Cliente seller, Cliente buyer, boolean required) {
    VentaFicha sale = new VentaFicha(); sale.id = UUID.randomUUID(); sale.numero = "V-1"; sale.motovehiculo = moto; sale.vendedor = seller; sale.comprador = buyer;
    VentaFichaItem item = new VentaFichaItem(); item.id = UUID.randomUUID(); item.fichaVenta = sale; item.etiqueta = "Formulario"; item.orden = 1; item.obligatorio = required;
    sale.items.add(item);
    return sale;
  }
  private static TransferenciaMoto transferencia(VentaFicha sale) {
    TransferenciaMoto transfer = new TransferenciaMoto(); transfer.id = UUID.randomUUID(); transfer.motovehiculo = sale.motovehiculo; transfer.clienteAnterior = sale.vendedor; transfer.clienteNuevo = sale.comprador; transfer.fichaVenta = sale;
    return transfer;
  }
  private static PropietarioMoto owner(Motovehiculo moto, Cliente client) { PropietarioMoto owner = new PropietarioMoto(); owner.motovehiculo = moto; owner.cliente = client; return owner; }
  private static Cliente cliente(String name) { Cliente client = new Cliente(); client.id = UUID.randomUUID(); client.nombre = name; client.activo = true; return client; }
  private static Motovehiculo moto() { Motovehiculo moto = new Motovehiculo(); moto.id = UUID.randomUUID(); moto.activo = true; moto.patente = "AA123AA"; moto.modelo = "Wave"; MarcaMoto brand = new MarcaMoto(); brand.id = UUID.randomUUID(); brand.nombre = "Honda"; moto.marca = brand; return moto; }
}
