package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrderStateTest {
  @Test void fichaLabelsAndMapping() {
    assertEquals(FichaState.INGRESADA, FichaState.of("Ingresada"));
    assertEquals(FichaState.EN_TRABAJO, FichaState.of("En trabajo"));
    assertEquals(FichaState.PARA_CONTROL, FichaState.of("Para control"));
    assertEquals(FichaState.PARA_ENTREGA, FichaState.of("Para entrega"));
    assertEquals(FichaState.ENTREGADA, FichaState.of("Entregada"));
    assertEquals(FichaState.CANCELADA, FichaState.of("Cancelada"));
    assertEquals("En trabajo", FichaState.EN_TRABAJO.label());
    assertThrows(BusinessException.class, () -> FichaState.of("Borrador"));
  }

  @Test
  void pagoMappings() {
    assertEquals(PagoState.PAGADO, PagoState.of("Pagado"));
    assertEquals(PagoState.PARCIAL, PagoState.of("Parcial"));
    assertEquals(PagoState.PENDIENTE, PagoState.of("Pendiente"));
    assertEquals("Pagado", PagoState.PAGADO.label());
    assertThrows(BusinessException.class, () -> PagoState.of("Saldo"));
  }

  @Test
  void trabajoMappings() {
    assertEquals(TrabajoState.REALIZADO, TrabajoState.of("Realizado"));
    assertEquals(TrabajoState.EN_PROCESO, TrabajoState.of("En proceso"));
    assertEquals(TrabajoState.PENDIENTE, TrabajoState.of("Pendiente"));
    assertEquals("Cancelado", TrabajoState.CANCELADO.label());
    assertThrows(BusinessException.class, () -> TrabajoState.of("Listo"));
  }

  @Test
  void repuestoMappings() {
    assertEquals(RepuestoPedidoState.EN_CURSO, RepuestoPedidoState.of("En curso"));
    assertEquals(RepuestoPedidoState.COMPLETADO, RepuestoPedidoState.of("Completado"));
    assertEquals("Cancelado", RepuestoPedidoState.CANCELADO.label());
    assertEquals(RepuestoPagoState.PAGADO, RepuestoPagoState.of("Pagado"));
    assertEquals(RepuestoPagoState.NO_PAGADO, RepuestoPagoState.of("No pagado"));
    assertEquals(RepuestoItemState.ENTREGADO, RepuestoItemState.of("Entregado"));
    assertEquals(RepuestoItemState.PEDIDO, RepuestoItemState.of("Pedido"));
    assertEquals("Pendiente de pedir", RepuestoItemState.PENDIENTE_DE_PEDIR.label());
    assertThrows(BusinessException.class, () -> RepuestoItemState.of("Comprado"));
  }

  @Test
  void revisionMappings() {
    assertEquals(RevisionControlState.APROBADO, RevisionControlState.of("Aprobado"));
    assertEquals(RevisionControlState.REQUIERE_CORRECCION, RevisionControlState.of("Requiere corrección"));
    assertEquals("No aplica", RevisionControlState.NO_APLICA.label());
    assertThrows(BusinessException.class, () -> RevisionControlState.of("Ok"));
  }

  @Test
  void estadoMotoLabels() {
    assertEquals(EstadoMoto.ACTIVA, EstadoMoto.of("Activa"));
    assertEquals(EstadoMoto.EN_TALLER, EstadoMoto.of("En taller"));
    assertEquals("Para entrega", EstadoMoto.PARA_ENTREGA.label());
    assertThrows(BusinessException.class, () -> EstadoMoto.of("Vendida"));
  }
}