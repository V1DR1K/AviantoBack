package com.avianto.back;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost")
class ApiControllerSecurityTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private ApiService api;
  @MockitoBean private AuthService auth;
  @MockitoBean private JwtService jwt;
  @MockitoBean private DataRepository db;

  @Test
  @WithMockUser(authorities = "ROLE_OPERARIO")
  void operatorCanSynchronizeSaleChecklist() throws Exception {
    mvc.perform(post("/api/ventas/{id}/checklist/sincronizar", UUID.randomUUID()))
      .andExpect(status().isOk());

    verify(api).sincronizarVentaChecklist(any(UUID.class));
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERARIO")
  void operatorCannotChangeSaleBuyer() throws Exception {
    mvc.perform(put("/api/ventas/{id}/comprador", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"compradorId\":\"" + UUID.randomUUID() + "\"}"))
      .andExpect(status().isForbidden());

    verifyNoInteractions(api);
  }

  @Test
  @WithMockUser(authorities = {"ROLE_OPERARIO", "PERM_FICHA_WRITE"})
  void operatorWithFichaPermissionCanRequestStateChange() throws Exception {
    mvc.perform(patch("/api/fichas/{id}/estado", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"estado\":\"En proceso\"}"))
      .andExpect(status().isOk());

    verify(api).fichaState(any(UUID.class), any(ApiDtos.StateRequest.class));
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERARIO")
  void operatorCannotRegisterPayment() throws Exception {
    mvc.perform(post("/api/fichas/{id}/pagos", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"monto\":10}"))
      .andExpect(status().isForbidden());

    verifyNoInteractions(api);
  }

  @Test
  @WithMockUser(authorities = "ROLE_ADMINISTRACION")
  void administratorCanRegisterPayment() throws Exception {
    mvc.perform(post("/api/fichas/{id}/pagos", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"monto\":10}"))
      .andExpect(status().isCreated());

    verify(api).registrarFichaPago(any(UUID.class), any(ApiDtos.PagoRegistroRequest.class));
  }

  @Test
  @WithMockUser(authorities = "ROLE_OPERARIO")
  void operatorCannotReadAudit() throws Exception {
    mvc.perform(get("/api/auditoria"))
      .andExpect(status().isForbidden());

    verifyNoInteractions(api);
  }
}
