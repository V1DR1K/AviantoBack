package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrderStateTest {
  @Test void fichaLabelsAndMapping() {
    assertEquals(FichaState.PENDIENTE, FichaState.of("Pendiente"));
    assertEquals(FichaState.EN_PROCESO, FichaState.of("En proceso"));
    assertEquals(FichaState.REVISION, FichaState.of("En revisión"));
    assertEquals(FichaState.TERMINADA, FichaState.of("Terminada"));
    assertEquals(FichaState.ENTREGADA, FichaState.of("Entregada"));
    assertEquals(FichaState.CANCELADA, FichaState.of("Cancelada"));
    assertEquals("En proceso", FichaState.EN_PROCESO.label());
    assertEquals("Pendiente", FichaState.PENDIENTE.label());
    assertThrows(BusinessException.class, () -> FichaState.of("Borrador"));
  }

  @Test void motorcycleStatesFollowTheirSection() {
    assertEquals(MotoState.DISPONIBLE, MotoState.of("Disponible"));
    assertEquals("Disponible", MotoState.DISPONIBLE.label());
    assertEquals(MotoState.INGRESADA_TALLER, MotoState.of("Ingresada Taller"));
    assertEquals(MotoState.EN_VENTA, MotoState.of("En venta"));
    assertEquals(MotoState.PENDIENTE, MotoState.of("Pendiente"));
    assertEquals(MotoState.TERMINADA, MotoState.of("Terminada"));
    assertEquals(MotoState.TRANSFERENCIA_EN_PROCESO, MotoState.of("Transferencia en proceso"));
    assertEquals("Vendida", MotoState.VENDIDA.label());
  }

  @Test
  void pagoMappings() {
    assertEquals(PagoState.PAGADO, PagoState.of("Pagado"));
    assertEquals(PagoState.PARCIAL, PagoState.of("Parcial"));
    assertEquals(PagoState.NO_PAGADO, PagoState.of("No pagado"));
    assertEquals("Pagado", PagoState.PAGADO.label());
    assertThrows(BusinessException.class, () -> PagoState.of("Saldo"));
  }

  @Test
  void trabajoMappings() {
    assertEquals(TrabajoState.REALIZADO, TrabajoState.of("Realizado"));
    assertEquals(TrabajoState.PENDIENTE, TrabajoState.of("Pendiente"));
    assertEquals(TrabajoState.CANCELADO, TrabajoState.of("Cancelado"));
    assertEquals("Cancelado", TrabajoState.CANCELADO.label());
    assertThrows(BusinessException.class, () -> TrabajoState.of("Listo"));
  }

  @Test
  void repuestoMappings() {
    assertEquals(RepuestoPedidoState.EN_CURSO, RepuestoPedidoState.of("En curso"));
    assertEquals(RepuestoPedidoState.COMPLETADO, RepuestoPedidoState.of("Completado"));
    assertEquals("Cancelado", RepuestoPedidoState.CANCELADO.label());
    assertEquals(PagoState.PAGADO, PagoState.of("Pagado"));
    assertEquals(PagoState.NO_PAGADO, PagoState.of("No pagado"));
    assertEquals(RepuestoItemState.ENTREGADO, RepuestoItemState.of("Entregado"));
    assertEquals(RepuestoItemState.PEDIDO, RepuestoItemState.of("Pedido"));
    assertEquals("Pendiente de pedir", RepuestoItemState.PENDIENTE_DE_PEDIR.label());
    assertThrows(BusinessException.class, () -> RepuestoItemState.of("Comprado"));
  }

  @Test
  void revisionMappings() {
    assertEquals(RevisionControlState.REVISADO, RevisionControlState.of("Revisado"));
    assertEquals(RevisionControlState.NO_APLICA, RevisionControlState.of("No aplica"));
    assertEquals("Revisado", RevisionControlState.REVISADO.label());
    assertThrows(BusinessException.class, () -> RevisionControlState.of("Ok"));
  }
}
