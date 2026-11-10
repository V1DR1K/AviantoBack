package com.avianto.back;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Catálogo único de permisos del sistema.
 *
 * <p>Cada permiso es un código {@code modulo.accion}. Los permisos {@code menu.*} controlan qué
 * secciones aparecen en el menú; el resto controla qué acciones puede hacer el usuario sobre los
 * datos. Los roles guardan un conjunto de estos códigos y cada endpoint exige el suyo con
 * {@code @PreAuthorize("hasAuthority('PERM_<codigo>')")}.
 */
final class Permisos {
  private Permisos() {}

  record Permiso(String codigo, String etiqueta) {}
  record Modulo(String id, String nombre, List<Permiso> permisos) {}

  static final String CODIGO_ADMINISTRACION = "ADMINISTRACION";
  static final String CODIGO_OPERARIO = "OPERARIO";

  private static Permiso p(String codigo, String etiqueta) { return new Permiso(codigo, etiqueta); }

  static final List<Modulo> CATALOGO = List.of(
      new Modulo("menu", "Secciones del menú", List.of(
          p("menu.inicio", "Inicio"),
          p("menu.perfiles", "Perfiles"),
          p("menu.wiki", "Wiki"),
          p("menu.fichas", "Fichas"),
          p("menu.pedidos", "Pedidos de repuestos"),
          p("menu.services", "Services"),
          p("menu.ventas", "Ventas"),
          p("menu.transferencias", "Transferencias"),
          p("menu.clientes", "Clientes"),
          p("menu.motos", "Motos"),
          p("menu.controles", "Controles"),
          p("menu.trabajos", "Trabajos"),
          p("menu.reportes", "Reportes"),
          p("menu.auditoria", "Auditoría"),
          p("menu.roles", "Roles"),
          p("menu.administracion", "Administración"))),
      new Modulo("dashboard", "Tableros", List.of(
          p("dashboard.ver", "Ver inicio y tableros"))),
      new Modulo("clientes", "Clientes", List.of(
          p("clientes.ver", "Ver"),
          p("clientes.crear", "Crear"),
          p("clientes.editar", "Editar"),
          p("clientes.eliminar", "Eliminar"))),
      new Modulo("motos", "Motos y perfiles", List.of(
          p("motos.ver", "Ver perfiles"),
          p("motos.crear", "Crear moto"),
          p("motos.editar", "Editar moto y foto"),
          p("motos.eliminar", "Eliminar moto"),
          p("motos.ingresar_taller", "Ingresar moto a Taller"),
          p("motos.ingresar_venta", "Ingresar moto a Ventas"),
          p("motos.circuito", "Pasar moto entre Taller y Ventas"))),
      new Modulo("services", "Services", List.of(
          p("services.ver", "Ver"),
          p("services.registrar", "Registrar service"),
          p("services.editar", "Editar service"))),
      new Modulo("fichas", "Fichas de trabajo", List.of(
          p("fichas.ver", "Ver"),
          p("fichas.crear", "Crear"),
          p("fichas.editar", "Editar ficha, trabajos y fotos"),
          p("fichas.estado", "Cambiar estado"),
          p("fichas.entregar", "Entregar moto"),
          p("fichas.eliminar", "Eliminar"))),
      new Modulo("revision", "Revisión", List.of(
          p("revision.controlar", "Marcar controles"),
          p("revision.aprobar", "Aprobar revisión"))),
      new Modulo("pagos", "Pagos", List.of(
          p("pagos.registrar", "Registrar pago"),
          p("pagos.anular", "Anular pago"))),
      new Modulo("pedidos", "Pedidos de repuestos", List.of(
          p("pedidos.ver", "Ver"),
          p("pedidos.crear", "Crear"),
          p("pedidos.editar", "Editar pedido e ítems"),
          p("pedidos.estado", "Cambiar estado"),
          p("pedidos.eliminar", "Eliminar"))),
      new Modulo("ventas", "Ventas", List.of(
          p("ventas.ver", "Ver"),
          p("ventas.editar", "Editar checklist e ítems"),
          p("ventas.comprador", "Asignar comprador"),
          p("ventas.transferir", "Gestionar transferencia"),
          p("ventas.completar", "Completar venta"))),
      new Modulo("transferencias", "Transferencias", List.of(
          p("transferencias.ver", "Ver"))),
      new Modulo("trabajos", "Catálogo de trabajos", List.of(
          p("trabajos.ver", "Ver"),
          p("trabajos.crear", "Crear"),
          p("trabajos.editar", "Editar"),
          p("trabajos.eliminar", "Eliminar"))),
      new Modulo("marcas", "Marcas de motos", List.of(
          p("marcas.ver", "Ver"),
          p("marcas.crear", "Crear"),
          p("marcas.editar", "Editar"),
          p("marcas.eliminar", "Eliminar"))),
      new Modulo("categorias", "Categorías de controles", List.of(
          p("categorias.ver", "Ver"),
          p("categorias.crear", "Crear"),
          p("categorias.editar", "Editar"),
          p("categorias.eliminar", "Eliminar"))),
      new Modulo("controles", "Controles de revisión", List.of(
          p("controles.ver", "Ver"),
          p("controles.crear", "Crear"),
          p("controles.editar", "Editar"),
          p("controles.eliminar", "Eliminar"))),
      new Modulo("checklist_venta", "Carpeta de transferencia (checklist de venta)", List.of(
          p("checklist_venta.ver", "Ver"),
          p("checklist_venta.crear", "Crear"),
          p("checklist_venta.editar", "Editar"),
          p("checklist_venta.eliminar", "Eliminar"))),
      new Modulo("reportes", "Reportes", List.of(
          p("reportes.ver", "Ver reportes de facturación"))),
      new Modulo("auditoria", "Auditoría", List.of(
          p("auditoria.ver", "Ver"))),
      new Modulo("usuarios", "Usuarios", List.of(
          p("usuarios.ver", "Ver"),
          p("usuarios.crear", "Crear"),
          p("usuarios.editar", "Editar"),
          p("usuarios.eliminar", "Eliminar"))),
      new Modulo("roles", "Roles y permisos", List.of(
          p("roles.ver", "Ver"),
          p("roles.crear", "Crear"),
          p("roles.editar", "Editar"),
          p("roles.eliminar", "Eliminar"))),
      new Modulo("respaldos", "Copias de seguridad", List.of(
          p("respaldos.ver", "Ver copias"),
          p("respaldos.editar", "Configurar copias y Google Drive"),
          p("respaldos.ejecutar", "Ejecutar copia"))),
      new Modulo("sistema", "Sistema", List.of(
          p("api.docs", "Ver documentación de la API"))));

  /** Todos los códigos del catálogo. El rol Administración los tiene siempre. */
  static final Set<String> TODOS = CATALOGO.stream()
      .flatMap(m -> m.permisos().stream())
      .map(Permiso::codigo)
      .collect(Collectors.toUnmodifiableSet());


  static boolean esValido(String codigo) { return TODOS.contains(codigo); }
}
