package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrderStateTest {
  @Test void mapsContractLabels() {
    assertEquals(OrderState.EN_PROCESO, OrderState.of("En proceso"));
    assertEquals("Pagado", OrderState.PAGADO.label());
    assertThrows(BusinessException.class, () -> OrderState.of("Borrador"));
  }
}
