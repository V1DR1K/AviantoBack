package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    Motovehiculo moto = moto(motoId); moto.patente = "AA123AA"; moto.modelo = "FZ"; MarcaMoto brand = new MarcaMoto(); brand.nombre = "Yamaha"; moto.marca = brand;
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

  private static ApiDtos.FichaRequest ficha(UUID clienteId, UUID motoId) {
    return new ApiDtos.FichaRequest(clienteId, motoId, null, null, null, null, null, BigDecimal.ZERO, false, List.of());
  }
  private static Cliente cliente(UUID id) { Cliente c = new Cliente(); c.id = id; c.nombre = "Cliente"; return c; }
  private static Motovehiculo moto(UUID id) { Motovehiculo m = new Motovehiculo(); m.id = id; m.activo = true; return m; }
}
