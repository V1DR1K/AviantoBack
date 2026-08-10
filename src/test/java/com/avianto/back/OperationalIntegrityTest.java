package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
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

  private static ApiDtos.FichaRequest ficha(UUID clienteId, UUID motoId) {
    return new ApiDtos.FichaRequest(clienteId, motoId, null, null, null, null, null, BigDecimal.ZERO, false, List.of());
  }
  private static Cliente cliente(UUID id) { Cliente c = new Cliente(); c.id = id; c.nombre = "Cliente"; return c; }
  private static Motovehiculo moto(UUID id) { Motovehiculo m = new Motovehiculo(); m.id = id; m.activo = true; return m; }
}
