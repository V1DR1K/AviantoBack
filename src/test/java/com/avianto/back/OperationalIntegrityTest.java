package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
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
    Motovehiculo moto = moto(motoId); moto.ingresada = false; moto.seccion = null; moto.estadoOperativo = null;
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);

    ApiDtos.MotorcycleResponse response = api.ingresarMoto(motoId, new ApiDtos.IntakeRequest("VENTA"));

    assertTrue(moto.ingresada);
    assertEquals(MotoSection.VENTA, moto.seccion);
    assertEquals(MotoState.INGRESADA_VENTA.label(), response.estado());
  }

  @Test
  void deliveryRejectsOpenWorkshopOperations() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.count(contains("from Ficha"), anyMap())).thenReturn(1L);

    assertThrows(BusinessException.class, () -> api.entregarMoto(motoId));
  }

  @Test
  void deliveryClosesAnIngresadaWorkshopMotorcycle() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.count(anyString(), anyMap())).thenReturn(0L);

    api.entregarMoto(motoId);

    assertFalse(moto.ingresada);
    assertEquals(MotoState.ENTREGADA, moto.estadoOperativo);
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
     Motovehiculo moto = moto(motoId); moto.seccion = MotoSection.VENTA; moto.estadoOperativo = MotoState.EN_VENTA; moto.ingresada = true; moto.patente = "AA123AA"; moto.modelo = "FZ"; MarcaMoto brand = new MarcaMoto(); brand.nombre = "Yamaha"; moto.marca = brand;
    Cliente oldClient = cliente(oldId); Cliente newClient = cliente(newId); newClient.nombre = "Nuevo cliente";
    PropietarioMoto current = new PropietarioMoto(); current.motovehiculo = moto; current.cliente = oldClient; current.fechaDesde = LocalDate.now().minusDays(10);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.get(Cliente.class, newId)).thenReturn(newClient);
    when(db.one(contains("from PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(current);

    ApiDtos.TransferResponse response = api.createTransfer(new ApiDtos.TransferRequest(motoId, newId, LocalDate.now(), "Venta"));

    assertEquals(newId, response.clienteNuevoId());
    assertEquals(LocalDate.now().minusDays(1), current.fechaHasta);
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
   private static Motovehiculo moto(UUID id) { Motovehiculo m = new Motovehiculo(); m.id = id; m.activo = true; m.seccion = MotoSection.TALLER; m.ingresada = true; m.estadoOperativo = MotoState.INGRESADA_TALLER; return m; }
}
