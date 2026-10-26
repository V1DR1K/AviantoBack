package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

class SwaggerSessionIndexTransformerTest {
  private final SwaggerIndexTransformer delegate = mock(SwaggerIndexTransformer.class);
  private final ResourceTransformerChain chain = mock(ResourceTransformerChain.class);
  private final SwaggerSessionIndexTransformer transformer = new SwaggerSessionIndexTransformer(delegate);
  private final HttpServletRequest request = new MockHttpServletRequest();

  @Test
  void addsSessionJwtInterceptorOnlyToSwaggerInitializer() throws Exception {
    Resource initializer = resource("https://example.test/swagger-ui/swagger-initializer.js",
        "window.onload = function() { window.ui = SwaggerUIBundle({\n"
            + "  url: \"https://petstore.swagger.io/v2/swagger.json\",\n"
            + "  \"configUrl\" : \"/v3/api-docs/swagger-config\",\n"
            + "}); };");
    when(delegate.transform(request, initializer, chain)).thenReturn(initializer);

    Resource transformed = transformer.transform(request, initializer, chain);
    String javascript = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(javascript.contains("requestInterceptor"));
    assertTrue(javascript.contains("avianto.session"));
    assertTrue(javascript.contains("ADMINISTRACION"));
    assertTrue(javascript.contains("target.origin !== window.location.origin"));
    assertTrue(javascript.contains("url: \"/v3/api-docs\""));
    assertFalse(javascript.contains("petstore.swagger.io"));
    assertFalse(javascript.contains("configUrl"));
    assertTrue(javascript.indexOf("requestInterceptor") < javascript.indexOf("SwaggerUIBundle({") + 30);
  }

  @Test
  void leavesOtherSwaggerAssetsUntouched() throws Exception {
    Resource index = resource("https://example.test/swagger-ui/index.html", "<html>swagger</html>");
    when(delegate.transform(request, index, chain)).thenReturn(index);

    assertSame(index, transformer.transform(request, index, chain));
  }

  private static Resource resource(String url, String content) throws Exception {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    URL resourceUrl = new URL(url);
    return new AbstractResource() {
      @Override public String getDescription() { return resourceUrl.toString(); }
      @Override public URL getURL() { return resourceUrl; }
      @Override public boolean exists() { return true; }
      @Override public long contentLength() { return bytes.length; }
      @Override public long lastModified() { return 0L; }
      @Override public java.io.InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
      }
    };
  }
}
