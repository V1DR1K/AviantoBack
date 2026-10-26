package com.avianto.back;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:forwarded-headers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "app.jwt.secret=forwarded-header-test-secret-with-more-than-32-bytes",
    "app.cors.allowed-origins=https://avianto.neticar.com.ar"
})
@AutoConfigureMockMvc
class OpenApiForwardedHeadersTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private SeedService seedService;

  @Test
  @WithMockUser(authorities = "ROLE_ADMINISTRACION")
  void openApiUsesOriginalHttpsHostFromReverseProxy() throws Exception {
    mvc.perform(get("/v3/api-docs")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "avianto.neticar.com.ar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.servers[0].url").value("https://avianto.neticar.com.ar"));
  }
}
