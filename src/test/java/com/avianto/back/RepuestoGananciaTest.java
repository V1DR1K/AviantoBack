package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RepuestoGananciaTest {
  private final DataRepository db = mock(DataRepository.class);
  private final ApiService api = new ApiService(db, mock(PasswordEncoder.class));

  @Test
  void newRepuestoItemsCarryTwentyPercentMarkupOnCost() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    Cliente cliente = cliente(UUID.randomUUID());
    PropietarioMoto owner = new PropietarioMoto();
    owner.motovehiculo = moto;
    owner.cliente = cliente;
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.get(Cliente.class, cliente.id)).thenReturn(cliente);
    when(db.one(contains("PropietarioMoto"), eq(PropietarioMoto.class), anyMap())).thenReturn(owner);
    ApiDtos.RepuestoItemRequest item = new ApiDtos.RepuestoItemRequest(
        "Filtro de aceite", RepuestoCategoria.REPUESTO, null, new BigDecimal("2"), new BigDecimal("100.00"), null, null);

    ApiDtos.RepuestoResponse response = api.createRepuesto(new ApiDtos.RepuestoRequest(
        motoId, cliente.id, null, LocalDate.now(), null, null, List.of(item)));

    ApiDtos.RepuestoItemResponse line = response.items().get(0);
    assertEquals(new BigDecimal("100.00"), line.precioCosto());
    assertEquals(new BigDecimal("120.00"), line.precioVenta());
    assertEquals(new BigDecimal("20"), line.porcentajeGanancia());
    assertEquals(new BigDecimal("200.00"), line.subtotalCosto());
    assertEquals(new BigDecimal("240.00"), line.subtotalVenta());
    assertEquals(new BigDecimal("200.00"), response.totalCosto());
    assertEquals(new BigDecimal("240.00"), response.totalVenta());
  }

  @Test
  void salePriceRoundsPerUnitToCentavos() {
    RepuestoPedidoItem item = new RepuestoPedidoItem();
    item.cantidad = BigDecimal.ONE;
    item.precio = new BigDecimal("33.33");
    item.porcentajeGanancia = new BigDecimal("20");

    fijarPreciosForTest(item);

    assertEquals(new BigDecimal("40.00"), item.precioVenta);
    assertEquals(new BigDecimal("40.00"), item.subtotalVenta);
  }

  @Test
  void billingGroupsByFichaMonthAndSkipsCancelledDocuments() {
    Ficha ficha = ficha(LocalDate.of(2026, 3, 10), "1000.00");
    Ficha cancelada = ficha(LocalDate.of(2026, 3, 12), "700.00");
    cancelada.estado = FichaState.CANCELADA;

    RepuestoPedido linked = pedido(ficha, null);
    linked.items.add(item(RepuestoCategoria.REPUESTO, "100.00", "120.00", RepuestoItemState.RECIBIDO));
    linked.items.add(item(RepuestoCategoria.ACCESORIO, "50.00", "60.00", RepuestoItemState.PEDIDO));
    linked.items.add(item(RepuestoCategoria.REPUESTO, "999.00", "999.00", RepuestoItemState.CANCELADO));

    RepuestoPedido sinFicha = pedido(null, LocalDate.of(2026, 4, 2));
    sinFicha.items.add(item(RepuestoCategoria.REPUESTO, "200.00", "240.00", RepuestoItemState.PEDIDO));

    RepuestoPedido delCancelada = pedido(cancelada, null);
    delCancelada.items.add(item(RepuestoCategoria.REPUESTO, "70.00", "84.00", RepuestoItemState.PEDIDO));

    when(db.all(contains("from Ficha e"), eq(Ficha.class), anyMap())).thenReturn(List.of(ficha));
    when(db.all(contains("from RepuestoPedido e"), eq(RepuestoPedido.class), anyMap()))
        .thenReturn(List.of(linked, sinFicha, delCancelada));

    List<ApiDtos.BillingMonthResponse> months = api.billing();

    assertEquals(2, months.size());
    ApiDtos.BillingMonthResponse marzo = months.get(0);
    assertEquals("2026-03", marzo.mes());
    assertEquals(new BigDecimal("1000.00"), marzo.fichasTotal());
    assertEquals(new BigDecimal("100.00"), marzo.repuestosCosto());
    assertEquals(new BigDecimal("120.00"), marzo.repuestosVenta());
    assertEquals(new BigDecimal("20.00"), marzo.repuestosGanancia());
    assertEquals(new BigDecimal("50.00"), marzo.accesoriosCosto());
    assertEquals(new BigDecimal("60.00"), marzo.accesoriosVenta());
    assertEquals(new BigDecimal("10.00"), marzo.accesoriosGanancia());
    assertEquals(new BigDecimal("0.00"), marzo.pedidosSinFichaVenta());

    ApiDtos.BillingMonthResponse abril = months.get(1);
    assertEquals("2026-04", abril.mes());
    assertEquals(new BigDecimal("200.00"), abril.pedidosSinFichaCosto());
    assertEquals(new BigDecimal("240.00"), abril.pedidosSinFichaVenta());
  }

  @Test
  void clientCanBeCreatedWithDniAndWithoutPhone() {
    when(db.count(contains("lower(e.documento)"), anyMap())).thenReturn(0L);

    ApiDtos.ClientResponse response = api.createClient(
        new ApiDtos.ClientRequest("Ana Pérez", "30111222", null, null, "Av. Siempre Viva 742", null));

    assertEquals("30111222", response.documento());
    assertNull(response.telefono());
    assertEquals("Av. Siempre Viva 742", response.direccion());
  }

  @Test
  void duplicateDniIsRejectedWithReadableMessage() {
    when(db.count(contains("lower(e.documento)"), anyMap())).thenReturn(1L);

    BusinessException error = assertThrows(BusinessException.class, () -> api.createClient(
        new ApiDtos.ClientRequest("Otro cliente", "30111222", null, null, null, null)));

    assertEquals(409, error.status);
    assertEquals("Ya existe un cliente con ese DNI", error.getMessage());
  }

  private void fijarPreciosForTest(RepuestoPedidoItem item) {
    try {
      var method = ApiService.class.getDeclaredMethod("fijarPrecios", RepuestoPedidoItem.class);
      method.setAccessible(true);
      method.invoke(null, item);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RepuestoPedidoItem item(RepuestoCategoria tipo, String costo, String venta, RepuestoItemState estado) {
    RepuestoPedidoItem item = new RepuestoPedidoItem();
    item.tipo = tipo;
    item.estado = estado;
    item.subtotal = new BigDecimal(costo);
    item.subtotalVenta = new BigDecimal(venta);
    return item;
  }

  private static RepuestoPedido pedido(Ficha ficha, LocalDate fecha) {
    RepuestoPedido pedido = new RepuestoPedido();
    pedido.id = UUID.randomUUID();
    pedido.numero = "R-" + pedido.id.toString().substring(0, 4);
    pedido.ficha = ficha;
    pedido.fecha = fecha == null ? LocalDate.of(2026, 3, 11) : fecha;
    return pedido;
  }

  private static Ficha ficha(LocalDate fechaIngreso, String total) {
    Ficha ficha = new Ficha();
    ficha.id = UUID.randomUUID();
    ficha.fechaIngreso = fechaIngreso;
    ficha.total = new BigDecimal(total);
    ficha.estado = FichaState.TERMINADA;
    return ficha;
  }

  private static Motovehiculo moto(UUID id) {
    Motovehiculo m = new Motovehiculo();
    m.id = id;
    m.activo = true;
    m.seccion = MotoSection.TALLER;
    m.ingresada = true;
    m.estadoOperativo = MotoState.INGRESADA_TALLER;
    m.marca = new MarcaMoto();
    m.marca.nombre = "Honda";
    m.modelo = "Wave";
    m.patente = "AB123CD";
    return m;
  }

  private static Cliente cliente(UUID id) {
    Cliente c = new Cliente();
    c.id = id;
    c.nombre = "Cliente";
    c.activo = true;
    return c;
  }
}
