package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PermisosCatalogoTest {
  private static final Pattern PERM = Pattern.compile("PERM_([A-Za-z0-9_.]+)");

  @Test
  void everyEndpointRequiresAPermissionFromTheCatalog() {
    List<String> desconocidos = new ArrayList<>();
    for (Class<?> controller : List.of(ApiController.class, BackupController.class)) {
      for (Method method : controller.getDeclaredMethods()) {
        PreAuthorize anotacion = method.getAnnotation(PreAuthorize.class);
        if (anotacion == null) continue;
        Matcher matcher = PERM.matcher(anotacion.value());
        boolean tienePermiso = false;
        while (matcher.find()) {
          tienePermiso = true;
          if (!Permisos.esValido(matcher.group(1))) desconocidos.add(controller.getSimpleName() + "." + method.getName() + " -> " + matcher.group(1));
        }
        assertTrue(tienePermiso, "Endpoint sin permiso: " + method.getName());
        assertFalse(anotacion.value().contains("ROLE_"), "Autorización por rol fijo en " + method.getName());
      }
    }
    assertTrue(desconocidos.isEmpty(), "Permisos inexistentes en el catálogo: " + desconocidos);
  }

  @Test
  void catalogHasUniqueCodesAndAdministrationHoldsAllOfThem() {
    List<String> codigos = Permisos.CATALOGO.stream().flatMap(m -> m.permisos().stream()).map(Permisos.Permiso::codigo).toList();
    assertEquals(codigos.size(), Set.copyOf(codigos).size(), "Códigos de permiso repetidos");
    assertTrue(Permisos.TODOS.containsAll(codigos));
    assertTrue(Permisos.TODOS.contains("menu.roles"));
    assertTrue(Permisos.TODOS.contains("roles.editar"));
  }
}
