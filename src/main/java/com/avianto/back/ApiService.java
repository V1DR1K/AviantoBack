package com.avianto.back;

import static com.avianto.back.ApiDtos.*;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApiService {
  private final DataRepository db; private final PasswordEncoder encoder;
  public ApiService(DataRepository db, PasswordEncoder encoder) { this.db = db; this.encoder = encoder; }

  private Map<String,Object> p() { return new HashMap<>(); }
  private UUID actorId() { try { return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName()); } catch(Exception e) { return null; } }
  AppUser actor() { UUID id = actorId(); return id == null ? null : db.get(AppUser.class, id); }
  void audit(String module, String action, String text) { Auditoria a = new Auditoria(); a.usuario = actor(); a.modulo = module; a.accion = action; a.descripcion = text; db.persist(a); }
  private void deleted(BaseEntity e) {
    if (e instanceof Cliente x) x.activo = false;
    if (e instanceof Motovehiculo x) x.activo = false;
    if (e instanceof ItemCatalogo x) x.activo = false;
    if (e instanceof ControlEntrega x) x.activo = false;
    if (e instanceof MarcaMoto x) x.activo = false;
    if (e instanceof CategoriaCatalogo x) x.activo = false;
    if (e instanceof AppUser x) x.activo = false;
    e.deletedAt = Instant.now(); e.deletedBy = actorId();
  }
  private String active(boolean includeDeleted) { return includeDeleted ? "" : " and e.deletedAt is null"; }
  private String dirOf(String dir) { return "DESC".equalsIgnoreCase(dir) ? "DESC" : "ASC"; }
  private String sortable(String requested, Set<String> allowed, String fallback) { return allowed.contains(requested) ? requested : fallback; }
  private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP); }
  private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static LocalDate today() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")); }

  private <T> PageResponse<T> page(String from, String where, String countFrom, Map<String,Object> ps, int pageId, int size, String sort, String dir, Function<Object,T> mapper) {
    if (pageId < 0 || !(size == 10 || size == 20 || size == 50 || size == 100)) throw new BusinessException(400, "Paginación inválida");
    String order = " order by e." + sort + " " + dirOf(dir);
    List<T> content = db.list("select e " + from + where + order, Object.class, ps, pageId, size).stream().map(mapper).toList();
    long total = db.count("select count(e) " + countFrom + where, ps);
    return new PageResponse<>(content, pageId, size, total, (int) Math.ceil(total / (double) size), sort, dirOf(dir));
  }

  // ---------- Clientes ----------
  public PageResponse<ClientResponse> clients(String q, Boolean activo, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (q != null && !q.isBlank()) { w += " and (lower(e.nombre) like :q or lower(coalesce(e.documento,'')) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (activo != null) { w += " and e.activo=:a"; ps.put("a", activo); }
    return page("from Cliente e", w, "from Cliente e", ps, page, size, sortable(sort, Set.of("nombre", "createdAt", "updatedAt"), "nombre"), dir, x -> client((Cliente) x));
  }
  public ClientResponse client(UUID id) { return client(db.get(Cliente.class, id)); }
  public ClientResponse createClient(ClientRequest r) { Cliente e = new Cliente(); copy(r, e); db.persist(e); audit("Clientes", "CREAR", e.nombre); return client(e); }
  public ClientResponse updateClient(UUID id, ClientRequest r) { Cliente e = db.get(Cliente.class, id); copy(r, e); audit("Clientes", "EDITAR", e.nombre); return client(e); }
  public void deleteClient(UUID id) {
    Cliente e = db.get(Cliente.class, id);
    if (db.count("select count(m) from Motovehiculo m where m.cliente.id=:id and m.deletedAt is null", Map.of("id", id)) > 0) throw new BusinessException(409, "El cliente tiene motos activas");
    deleted(e); audit("Clientes", "ELIMINAR", e.nombre);
  }
  private void copy(ClientRequest r, Cliente e) { e.nombre = r.nombre().trim(); e.documento = blank(r.documento()); e.telefono = r.telefono().trim(); e.email = blank(r.email()); e.direccion = blank(r.direccion()); e.observaciones = blank(r.observaciones()); }
  private ClientResponse client(Cliente e) {
    long motos = db.count("select count(m) from Motovehiculo m where m.cliente.id=:id and m.deletedAt is null", Map.of("id", e.id));
    long fichas = db.count("select count(o) from Pedido o where o.cliente.id=:id and o.deletedAt is null", Map.of("id", e.id));
    return new ClientResponse(e.id, e.nombre, e.documento, e.telefono, e.email, e.direccion, e.observaciones, e.activo, motos, fichas, e.createdAt, e.updatedAt);
  }
  @Cacheable(value = "autocomplete", key = "'clients:' + #q") public List<AutocompleteResponse> clientAutocomplete(String q) {
    return db.all("select e from Cliente e where e.deletedAt is null and e.activo=true and (lower(e.nombre) like :q or lower(coalesce(e.documento,'')) like :q) order by e.nombre", Cliente.class, Map.of("q", "%" + q.toLowerCase() + "%")).stream().limit(15).map(e -> new AutocompleteResponse(e.id, e.nombre, e.documento)).toList();
  }
  @CacheEvict(value = "autocomplete", allEntries = true) void clearAutocomplete() {}

  // ---------- Motovehículos ----------
  public PageResponse<MotorcycleResponse> motorcycles(String q, UUID clientId, UUID brandId, String estado, Boolean activo, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (q != null && !q.isBlank()) { w += " and (lower(e.modelo) like :q or lower(e.patente) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (clientId != null) { w += " and e.cliente.id=:client"; ps.put("client", clientId); }
    if (brandId != null) { w += " and e.marca.id=:brand"; ps.put("brand", brandId); }
    if (estado != null && !estado.isBlank()) { w += " and e.estado=:estado"; ps.put("estado", EstadoMoto.of(estado)); }
    if (activo != null) { w += " and e.activo=:activo"; ps.put("activo", activo); }
    return page("from Motovehiculo e join e.cliente join e.marca", w, "from Motovehiculo e", ps, page, size, sortable(sort, Set.of("modelo", "patente", "estado", "kilometraje", "createdAt", "updatedAt"), "patente"), dir, x -> motorcycle((Motovehiculo) x));
  }
  public MotorcycleResponse motorcycle(UUID id) { return motorcycle(db.get(Motovehiculo.class, id)); }
  public MotorcycleResponse createMotorcycle(MotorcycleRequest r) { Motovehiculo e = new Motovehiculo(); copy(r, e); db.persist(e); audit("Motovehículos", "CREAR", e.patente); clearAutocomplete(); return motorcycle(e); }
  public MotorcycleResponse updateMotorcycle(UUID id, MotorcycleRequest r) { Motovehiculo e = db.get(Motovehiculo.class, id); copy(r, e); audit("Motovehículos", "EDITAR", e.patente); clearAutocomplete(); return motorcycle(e); }
  public void deleteMotorcycle(UUID id) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (db.count("select count(o) from Pedido o where o.motovehiculo.id=:id and o.deletedAt is null", Map.of("id", id)) > 0) throw new BusinessException(409, "La moto tiene fichas activas");
    deleted(e); audit("Motovehículos", "ELIMINAR", e.patente); clearAutocomplete();
  }
  public MotorcycleResponse updateMotoConfig(UUID id, MotoConfigServiceRequest r) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (r.kmServicePeriodo() != null) e.kmServicePeriodo = r.kmServicePeriodo();
    if (r.mesesServicePeriodo() != null) e.mesesServicePeriodo = r.mesesServicePeriodo();
    e.serviceObservaciones = blank(r.serviceObservaciones());
    audit("Motovehículos", "CONFIG SERVICE", e.patente); return motorcycle(e);
  }
  private void copy(MotorcycleRequest r, Motovehiculo e) {
    Cliente c = db.get(Cliente.class, r.clienteId());
    MarcaMoto b = db.get(MarcaMoto.class, r.marcaId());
    if (!c.activo || c.deletedAt != null || !b.activo || b.deletedAt != null) throw new BusinessException(409, "Cliente o marca inactiva");
    e.cliente = c; e.marca = b; e.modelo = r.modelo().trim(); e.patente = r.patente().trim().toUpperCase(); e.anio = r.anio(); e.kilometraje = r.kilometraje(); e.observaciones = blank(r.observaciones());
  }
  private MotorcycleResponse motorcycle(Motovehiculo e) {
    return new MotorcycleResponse(e.id, e.cliente.id, e.marca.id, e.cliente.nombre, e.marca.nombre, e.modelo, e.patente, e.anio, e.kilometraje, e.estado.label(), e.kmUltimoService, e.fechaUltimoService, e.kmServicePeriodo, e.mesesServicePeriodo, e.serviceObservaciones, e.observaciones, e.activo, e.createdAt, e.updatedAt);
  }
  @Cacheable(value = "autocomplete", key = "'motorcycles:' + #q") public List<AutocompleteResponse> motorcycleAutocomplete(String q) {
    return db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true and (lower(e.modelo) like lower(:q) or lower(e.patente) like lower(:q)) order by e.patente", Motovehiculo.class, Map.of("q", "%" + q.toLowerCase() + "%")).stream().limit(15).map(e -> new AutocompleteResponse(e.id, e.patente, e.marca.nombre + " " + e.modelo)).toList();
  }

  // ---------- Propietarios ----------
  public List<OwnerResponse> owners(UUID motoId) {
    return db.all("select e from PropietarioMoto e join e.cliente where e.motovehiculo.id=:moto and e.deletedAt is null order by e.fechaDesde desc", PropietarioMoto.class, Map.of("moto", motoId)).stream().map(this::owner).toList();
  }
  public OwnerResponse addOwner(UUID motoId, OwnerRequest r) {
    Motovehiculo m = db.get(Motovehiculo.class, motoId);
    Cliente c = db.get(Cliente.class, r.clienteId());
    if (!c.activo || c.deletedAt != null) throw new BusinessException(409, "Cliente inactivo");
    LocalDate inicio = r.fechaDesde() == null ? today() : r.fechaDesde();
    for (PropietarioMoto act : db.all("select e from PropietarioMoto e where e.motovehiculo.id=:moto and e.fechaHasta is null and e.deletedAt is null", PropietarioMoto.class, Map.of("moto", motoId))) {
      if (act.cliente.id.equals(c.id)) throw new BusinessException(409, "El cliente ya es el propietario actual");
      act.fechaHasta = inicio;
    }
    PropietarioMoto n = new PropietarioMoto(); n.motovehiculo = m; n.cliente = c; n.fechaDesde = inicio; n.observaciones = blank(r.observaciones());
    db.persist(n);
    m.cliente = c;
    audit("Propietarios", "CAMBIAR", m.patente + " -> " + c.nombre);
    clearAutocomplete();
    return owner(n);
  }
  private OwnerResponse owner(PropietarioMoto e) { return new OwnerResponse(e.id, e.cliente.id, e.cliente.nombre, e.fechaDesde, e.fechaHasta, e.fechaHasta == null && e.motovehiculo.cliente.id.equals(e.cliente.id), e.observaciones); }

  // ---------- Service ----------
  public List<ServiceResponse> services(UUID motoId) {
    return db.all("select e from ServiceMoto e join e.motovehiculo left join e.ficha where e.motovehiculo.id=:moto order by e.fecha desc", ServiceMoto.class, Map.of("moto", motoId)).stream().map(this::service).toList();
  }
  public ServiceResponse addService(UUID motoId, ServiceRequest req) {
    if (req.kilometraje() == null || req.kilometraje() < 0) throw new BusinessException(400, "Kilometraje inválido");
    Motovehiculo m = db.get(Motovehiculo.class, motoId);
    if (m.kmUltimoService != null && req.kilometraje() < m.kmUltimoService) throw new BusinessException(409, "El kilometraje no puede ser menor al último service");
    ServiceMoto e = new ServiceMoto();
    e.motovehiculo = m; e.ficha = (req.fichaId() == null ? null : db.get(Pedido.class, req.fichaId()));
    e.kilometraje = req.kilometraje(); e.fecha = req.fecha() == null ? today() : req.fecha(); e.observaciones = blank(req.observaciones()); e.realizadoPor = actor();
    db.persist(e);
    m.kmUltimoService = e.kilometraje; m.fechaUltimoService = e.fecha;
    audit("Services", "REGISTRAR", m.patente + " km " + e.kilometraje);
    return service(e);
  }
  private ServiceResponse service(ServiceMoto e) { return new ServiceResponse(e.id, e.motovehiculo.id, e.ficha == null ? null : e.ficha.id, e.kilometraje, e.fecha, e.observaciones, e.realizadoPor == null ? null : e.realizadoPor.nombre, e.createdAt); }
  public List<NextServiceResponse> nextServices() {
    LocalDate h = today();
    return db.all("select e from Motovehiculo e join e.cliente join e.marca where e.deletedAt is null and e.activo=true order by e.patente", Motovehiculo.class, Map.of()).stream().map(m -> {
      int perKm = m.kmServicePeriodo == null ? 5000 : m.kmServicePeriodo;
      int perM = m.mesesServicePeriodo == null ? 6 : m.mesesServicePeriodo;
      Integer base = m.kmUltimoService != null ? m.kmUltimoService : m.kilometraje;
      Integer proxKm = base == null ? null : base + perKm;
      int kmFaltan = proxKm == null ? 0 : Math.max(0, proxKm - (m.kilometraje == null ? 0 : m.kilometraje));
      boolean atrasadoKm = proxKm != null && m.kilometraje != null && m.kilometraje >= proxKm;
      LocalDate proxFecha = m.fechaUltimoService == null ? null : m.fechaUltimoService.plusMonths(perM);
      Long diasFaltan = proxFecha == null ? null : ChronoUnit.DAYS.between(h, proxFecha);
      boolean atrasadoFecha = proxFecha != null && proxFecha.isBefore(h);
      boolean sinRef = m.kmUltimoService == null && m.fechaUltimoService == null;
      return new NextServiceResponse(m.id, m.patente, m.cliente.nombre, m.marca.nombre + " " + m.modelo, m.kilometraje, m.kmUltimoService, m.fechaUltimoService, m.kmServicePeriodo, m.mesesServicePeriodo, proxKm, kmFaltan, proxFecha, diasFaltan, atrasadoKm, atrasadoFecha, sinRef);
    }).toList();
  }

  // ---------- Catálogo ----------
  @Cacheable("catalog") public PageResponse<CatalogResponse> catalog(String q, ItemType tipo, UUID categoria, Boolean activo, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (q != null && !q.isBlank()) { w += " and lower(e.descripcion) like :q"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (tipo != null) { w += " and e.tipo=:tipo"; ps.put("tipo", tipo); }
    if (categoria != null) { w += " and e.categoria.id=:categoria"; ps.put("categoria", categoria); }
    if (activo != null) { w += " and e.activo=:activo"; ps.put("activo", activo); }
    return page("from ItemCatalogo e join e.categoria", w, "from ItemCatalogo e", ps, page, size, sortable(sort, Set.of("descripcion", "precioBase", "createdAt", "updatedAt"), "descripcion"), dir, x -> item((ItemCatalogo) x));
  }
  public CatalogResponse catalogItem(UUID id) { return item(db.get(ItemCatalogo.class, id)); }
  @CacheEvict(value = "catalog", allEntries = true)
  public CatalogResponse createCatalog(CatalogRequest r) { ItemCatalogo e = new ItemCatalogo(); copy(r, e); db.persist(e); price(e, null, e.precioBase); audit("Catálogo", "CREAR", e.descripcion); return item(e); }
  @CacheEvict(value = "catalog", allEntries = true)
  public CatalogResponse updateCatalog(UUID id, CatalogRequest r) { ItemCatalogo e = db.get(ItemCatalogo.class, id); BigDecimal prior = e.precioBase; copy(r, e); if (prior.compareTo(e.precioBase) != 0) { price(e, prior, e.precioBase); } audit("Catálogo", "EDITAR", e.descripcion); return item(e); }
  @CacheEvict(value = "catalog", allEntries = true) public void deleteCatalog(UUID id) { ItemCatalogo e = db.get(ItemCatalogo.class, id); deleted(e); audit("Catálogo", "ELIMINAR", e.descripcion); }
  private void copy(CatalogRequest r, ItemCatalogo e) {
    CategoriaCatalogo c = db.get(CategoriaCatalogo.class, r.categoriaId());
    if (!c.activo || c.deletedAt != null) throw new BusinessException(409, "Categoría inactiva");
    e.descripcion = r.descripcion().trim(); e.tipo = r.tipo(); e.categoria = c;
    e.precioBase = money(r.precioBase()); e.observaciones = blank(r.observaciones());
  }
  private void price(ItemCatalogo e, BigDecimal old, BigDecimal value) { PriceHistory h = new PriceHistory(); h.item = e; h.precioAnterior = old; h.precioNuevo = value; h.changedBy = actorId(); db.persist(h); audit("Catálogo", "PRECIO", e.descripcion + ": " + value); }
  private CatalogResponse item(ItemCatalogo e) { return new CatalogResponse(e.id, e.descripcion, e.tipo, e.categoria.id, e.categoria.nombre, e.precioBase, e.observaciones, e.activo, e.createdAt, e.updatedAt); }
  public List<Map<String,Object>> priceHistory(UUID id) {
    return db.all("select h from PriceHistory h where h.item.id=:id order by h.changedAt desc", PriceHistory.class, Map.of("id", id)).stream().map(h -> Map.<String,Object>of("id", h.id, "precioAnterior", h.precioAnterior == null ? BigDecimal.ZERO : h.precioAnterior, "precioNuevo", h.precioNuevo, "fecha", h.changedAt)).toList();
  }
  public List<CatalogResponse> duplicates(String desc) { return db.all("select e from ItemCatalogo e where e.deletedAt is null and lower(e.descripcion)=lower(:d)", ItemCatalogo.class, Map.of("d", desc.trim())).stream().map(this::item).toList(); }

  // ---------- Fichas de trabajo ----------
  public PageResponse<FichaResponse> fichas(String q, UUID clienteId, UUID motoId, String patente, String estado, String estadoPago, LocalDate desde, LocalDate hasta, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (q != null && !q.isBlank()) { w += " and (lower(e.numero) like :q or lower(e.cliente.nombre) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (clienteId != null) { w += " and e.cliente.id=:c"; ps.put("c", clienteId); }
    if (motoId != null) { w += " and e.motovehiculo.id=:m"; ps.put("m", motoId); }
    if (patente != null && !patente.isBlank()) { w += " and lower(e.motovehiculo.patente) like :pat"; ps.put("pat", "%" + patente.toLowerCase() + "%"); }
    if (estado != null && !estado.isBlank()) { w += " and e.estado=:s"; ps.put("s", FichaState.of(estado)); }
    if (estadoPago != null && !estadoPago.isBlank()) { w += " and e.estadoPago=:pag"; ps.put("pag", PagoState.of(estadoPago)); }
    if (desde != null) { w += " and e.fechaIngreso>=:desde"; ps.put("desde", desde); }
    if (hasta != null) { w += " and e.fechaIngreso<=:hasta"; ps.put("hasta", hasta); }
    return page("from Pedido e join e.cliente join e.motovehiculo", w, "from Pedido e", ps, page, size, sortable(sort, Set.of("createdAt", "fechaIngreso", "estado", "total", "numero"), "fechaIngreso"), dir, x -> ficha((Pedido) x));
  }
  public FichaResponse ficha(UUID id) { return ficha(db.get(Pedido.class, id)); }
  public FichaResponse createFicha(FichaRequest r) {
    Pedido e = new Pedido(); copy(r, e);
    e.numero = "F-" + System.currentTimeMillis() + "-" + e.id.toString().substring(0, 4).toUpperCase();
    db.persist(e); e.motovehiculo.estado = EstadoMoto.EN_TALLER;
    audit("Fichas", "CREAR", e.numero);
    return ficha(e);
  }
  public FichaResponse updateFicha(UUID id, FichaRequest r) { Pedido e = db.get(Pedido.class, id); assertEditable(e); e.items.clear(); copy(r, e); audit("Fichas", "EDITAR", e.numero); return ficha(e); }
  public void deleteFicha(UUID id) { Pedido e = db.get(Pedido.class, id); if (e.estado == FichaState.ENTREGADA) throw new BusinessException(409, "No puede eliminarse una ficha entregada"); deleted(e); audit("Fichas", "ELIMINAR", e.numero); }
  public FichaResponse fichaState(UUID id, StateRequest r) {
    Pedido e = db.get(Pedido.class, id);
    FichaState next = FichaState.of(r.estado());
    if (!validTransition(e.estado, next)) throw new BusinessException(422, "Transición de estado inválida");
    if (next == FichaState.ENTREGADA && e.fechaEntregaReal == null) e.fechaEntregaReal = today();
    e.estado = next; syncMoto(e);
    audit("Fichas", "ESTADO", e.numero + " -> " + next.label());
    return ficha(e);
  }
  public FichaResponse fichaPago(UUID id, PagoRequest r) { Pedido e = db.get(Pedido.class, id); e.estadoPago = PagoState.of(r.estadoPago()); audit("Fichas", "PAGO", e.numero + " -> " + e.estadoPago.label()); return ficha(e); }
  private boolean validTransition(FichaState from, FichaState next) {
    if (from == next || from == FichaState.ENTREGADA || from == FichaState.CANCELADA) return false;
    if (next == FichaState.CANCELADA) return true;
    if (next == FichaState.ENTREGADA) return from == FichaState.PARA_ENTREGA || from == FichaState.PARA_CONTROL || from == FichaState.EN_TRABAJO;
    if (next == FichaState.PARA_ENTREGA) return from == FichaState.INGRESADA || from == FichaState.EN_TRABAJO || from == FichaState.PARA_CONTROL;
    if (next == FichaState.PARA_CONTROL) return from == FichaState.INGRESADA || from == FichaState.EN_TRABAJO;
    if (next == FichaState.EN_TRABAJO) return from == FichaState.INGRESADA;
    return false;
  }
  private void syncMoto(Pedido e) {
    Motovehiculo m = e.motovehiculo;
    if (e.estado == FichaState.ENTREGADA) { m.estado = EstadoMoto.ACTIVA; if (e.kilometrajeIngreso != null) m.kilometraje = e.kilometrajeIngreso; }
    else if (e.estado == FichaState.PARA_ENTREGA) m.estado = EstadoMoto.PARA_ENTREGA;
    else if (e.estado == FichaState.CANCELADA) { if (m.estado == EstadoMoto.EN_TALLER || m.estado == EstadoMoto.PARA_ENTREGA) m.estado = EstadoMoto.ACTIVA; }
    else m.estado = EstadoMoto.EN_TALLER;
  }
  private void assertEditable(Pedido e) { if (e.estado == FichaState.ENTREGADA || e.estado == FichaState.CANCELADA) throw new BusinessException(409, "La ficha ya finalizó"); }
  private void copy(FichaRequest r, Pedido e) {
    Cliente c = db.get(Cliente.class, r.clienteId());
    Motovehiculo m = db.get(Motovehiculo.class, r.motoId());
    if (!m.cliente.id.equals(c.id)) throw new BusinessException(409, "La moto no pertenece al cliente");
    if (!c.activo || !m.activo || c.deletedAt != null || m.deletedAt != null) throw new BusinessException(409, "Cliente o moto inactivo");
    e.cliente = c; e.motovehiculo = m;
    e.documento = r.documento();
    e.fechaIngreso = r.fechaIngreso() == null ? today() : r.fechaIngreso();
    e.fechaEntregaEstimada = r.fechaEntregaEstimada();
    e.kilometrajeIngreso = r.kilometrajeIngreso();
    e.vencimiento = r.vencimiento();
    e.observaciones = blank(r.observaciones());
    e.descuentoGlobal = money(r.descuentoGlobal());
    e.iva = r.documento() == DocumentType.Factura && r.iva();
    BigDecimal sum = BigDecimal.ZERO;
    if (r.items() != null) for (FichaItemRequest pos : r.items()) {
      applyFichaItem(e, pos, null);
      sum = sum.add(e.items.get(e.items.size() - 1).subtotal);
    }
    e.total = sum.subtract(e.descuentoGlobal);
    if (e.total.signum() < 0) throw new BusinessException(400, "Descuento global inválido");
    e.total = money(e.iva ? e.total.multiply(new BigDecimal("1.21")) : e.total);
  }
  private void applyFichaItem(Pedido e, FichaItemRequest r, UUID id) {
    PedidoItem i = new PedidoItem();
    if (id != null) i.id = id;
    i.pedido = e;
    if (r.itemCatalogoId() != null) i.itemCatalogo = db.get(ItemCatalogo.class, r.itemCatalogoId());
    i.descripcion = r.descripcion().trim();
    i.tipo = r.tipo();
    i.cantidad = r.cantidad() == null ? BigDecimal.ONE : r.cantidad();
    if (i.cantidad.stripTrailingZeros().scale() > 0) throw new BusinessException(400, "La cantidad debe ser un número entero");
    i.precioUnitario = money(r.precioUnitario());
    i.descuento = money(r.descuento());
    i.subtotal = money(i.cantidad.multiply(i.precioUnitario).subtract(i.descuento));
    if (i.subtotal.signum() < 0) throw new BusinessException(400, "Descuento de línea inválido");
    i.estadoTrabajo = (r.estadoTrabajo() == null || r.estadoTrabajo().isBlank()) ? TrabajoState.PENDIENTE : TrabajoState.of(r.estadoTrabajo());
    i.observacionTrabajo = blank(r.observacionTrabajo());
    if (i.estadoTrabajo == TrabajoState.REALIZADO) { i.completadoAt = Instant.now(); i.completadoPor = actorId(); }
    e.items.add(i);
  }
  private void recalc(Pedido e) {
    BigDecimal sum = e.items.stream().map(x -> x.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    sum = sum.subtract(e.descuentoGlobal);
    if (sum.signum() < 0) throw new BusinessException(400, "Descuento global inválido");
    e.total = money(e.iva ? sum.multiply(new BigDecimal("1.21")) : sum);
  }
  private FichaResponse ficha(Pedido e) {
    List<FichaItemResponse> lines = e.items.stream().map(i -> new FichaItemResponse(i.id, i.itemCatalogo == null ? null : i.itemCatalogo.id, i.descripcion, i.tipo, i.cantidad, i.precioUnitario, i.descuento, i.subtotal, i.estadoTrabajo.label(), i.observacionTrabajo, i.completadoAt, i.completadoPor)).toList();
    List<PhotoResponse> fotos = e.fotos.stream().map(f -> photo(e.id, f)).toList();
    return new FichaResponse(e.id, e.numero, e.cliente.id, e.motovehiculo.id, e.cliente.nombre, e.motovehiculo.marca.nombre + " " + e.motovehiculo.modelo, e.motovehiculo.patente, e.documento, e.vencimiento, e.fechaIngreso, e.fechaEntregaEstimada, e.fechaEntregaReal, e.kilometrajeIngreso, e.observaciones, e.descuentoGlobal, e.iva, e.estado.label(), e.estadoPago.label(), e.total, e.createdAt, lines, fotos);
  }
  public FichaResponse addFichaItem(UUID id, FichaItemRequest r) { Pedido e = db.get(Pedido.class, id); assertEditable(e); applyFichaItem(e, r, null); recalc(e); if (e.estado == FichaState.INGRESADA) { e.estado = FichaState.EN_TRABAJO; } audit("Fichas", "TRABAJO", e.numero); return ficha(e); }
  public FichaResponse updateFichaItem(UUID id, UUID itemId, FichaItemRequest r) {
    Pedido e = db.get(Pedido.class, id); assertEditable(e);
    PedidoItem old = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    e.items.remove(old); applyFichaItem(e, r, itemId); recalc(e);
    audit("Fichas", "TRABAJO", e.numero); return ficha(e);
  }
  public void deleteFichaItem(UUID id, UUID itemId) {
    Pedido e = db.get(Pedido.class, id); assertEditable(e);
    PedidoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    e.items.remove(target); recalc(e); audit("Fichas", "QUITAR TRABAJO", e.numero);
  }
  public FichaResponse fichaItemState(UUID id, UUID itemId, StateRequest r) {
    Pedido e = db.get(Pedido.class, id);
    PedidoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    TrabajoState next = TrabajoState.of(r.estado());
    if (!validTrabajo(target.estadoTrabajo, next)) throw new BusinessException(422, "Transición de trabajo inválida");
    target.estadoTrabajo = next;
    if (next == TrabajoState.REALIZADO) { target.completadoAt = Instant.now(); target.completadoPor = actorId(); }
    audit("Fichas", "TRABAJO ESTADO", e.numero + " " + target.descripcion + " -> " + next.label());
    if (next == TrabajoState.REALIZADO || next == TrabajoState.CANCELADO) {
      boolean allDone = !e.items.isEmpty() && e.items.stream().allMatch(x -> x.estadoTrabajo == TrabajoState.REALIZADO || x.estadoTrabajo == TrabajoState.CANCELADO);
      if (allDone && (e.estado == FichaState.EN_TRABAJO || e.estado == FichaState.INGRESADA)) {
        e.estado = FichaState.PARA_CONTROL;
      }
    }
    return ficha(e);
  }
  private boolean validTrabajo(TrabajoState from, TrabajoState next) {
    if (from == next || from == TrabajoState.CANCELADO) return false;
    if (next == TrabajoState.CANCELADO) return from != TrabajoState.REALIZADO;
    if (from == TrabajoState.REALIZADO) return false;
    return next == TrabajoState.EN_PROCESO || next == TrabajoState.REALIZADO;
  }

  // ---------- Fotos ----------
  public PhotoResponse createPhoto(UUID id, PhotoRequest r) {
    Pedido e = db.get(Pedido.class, id);
    byte[] data;
    try { data = Base64.getDecoder().decode(r.base64()); } catch (IllegalArgumentException ex) { throw new BusinessException(400, "base64 inválido"); }
    if (data.length > 5_000_000) throw new BusinessException(400, "Foto excede 5 MB");
    if (!"image/webp".equalsIgnoreCase(r.contentType()) || !webp(data)) throw new BusinessException(400, "La foto debe estar en formato WebP");
    PedidoFoto f = new PedidoFoto();
    f.pedido = e;
    f.filename = r.filename().replaceAll("[^a-zA-Z0-9._-]", "_").replaceFirst("(?i)\\.[^.]+$", "") + ".webp";
    f.contentType = "image/webp"; f.content = data;
    db.persist(f);
    audit("Fichas", "FOTO", e.numero);
    return photo(e.id, f);
  }
  public PedidoFoto photo(UUID fichaId, UUID photoId) {
    PedidoFoto f = db.one("select f from PedidoFoto f where f.pedido.id=:ficha and f.id=:photo", PedidoFoto.class, Map.of("ficha", fichaId, "photo", photoId));
    if (f == null) throw new NotFoundException("Imagen inexistente");
    return f;
  }
  private static boolean webp(byte[] data) { return data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P'; }
  private PhotoResponse photo(UUID fichaId, PedidoFoto f) { return new PhotoResponse(f.id, f.filename, f.contentType, f.createdAt, "/fichas/" + fichaId + "/fotos/" + f.id); }

  // ---------- Pedidos de repuestos ----------
  public PageResponse<RepuestoResponse> repuestos(String estado, String estadoPago, UUID motoId, UUID clienteId, String q, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (estado != null && !estado.isBlank()) { w += " and e.estado=:s"; ps.put("s", RepuestoPedidoState.of(estado)); }
    if (estadoPago != null && !estadoPago.isBlank()) { w += " and e.estadoPago=:p"; ps.put("p", RepuestoPagoState.of(estadoPago)); }
    if (motoId != null) { w += " and e.motovehiculo.id=:m"; ps.put("m", motoId); }
    if (clienteId != null) { w += " and e.cliente.id=:c"; ps.put("c", clienteId); }
    if (q != null && !q.isBlank()) { w += " and (lower(e.numero) like :q or lower(coalesce(e.proveedor,'')) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    return page("from PedidoRepuesto e join e.motovehiculo join e.cliente", w, "from PedidoRepuesto e", ps, page, size, sortable(sort, Set.of("fecha", "createdAt", "total", "estado"), "fecha"), dir, x -> repuesto((PedidoRepuesto) x));
  }
  public RepuestoResponse repuesto(UUID id) { return repuesto(db.get(PedidoRepuesto.class, id)); }
  public RepuestoResponse createRepuesto(RepuestoRequest r) {
    if (r.items() == null || r.items().isEmpty()) throw new BusinessException(400, "El pedido debe tener al menos un ítem");
    Motovehiculo m = db.get(Motovehiculo.class, r.motoVehiculoId());
    Cliente c = db.get(Cliente.class, r.clienteId());
    if (!m.cliente.id.equals(c.id)) throw new BusinessException(409, "La moto no pertenece al cliente");
    PedidoRepuesto e = new PedidoRepuesto();
    e.motovehiculo = m; e.cliente = c;
    e.ficha = r.fichaId() == null ? null : db.get(Pedido.class, r.fichaId());
    e.numero = "R-" + System.currentTimeMillis() + "-" + e.id.toString().substring(0, 4).toUpperCase();
    e.fecha = r.fecha() == null ? today() : r.fecha();
    e.proveedor = blank(r.proveedor());
    e.observaciones = blank(r.observaciones());
    for (RepuestoItemRequest ri : r.items()) applyRepuestoItem(e, ri, null);
    recalcRepuesto(e);
    db.persist(e);
    audit("Repuestos", "CREAR", e.numero);
    return repuesto(e);
  }
  public RepuestoResponse updateRepuesto(UUID id, RepuestoRequest r) {
    PedidoRepuesto e = db.get(PedidoRepuesto.class, id); assertRepuestoEditable(e);
    if (r.items() == null || r.items().isEmpty()) throw new BusinessException(400, "El pedido debe tener al menos un ítem");
    if (r.fichaId() != null) e.ficha = db.get(Pedido.class, r.fichaId());
    e.fecha = r.fecha() == null ? today() : r.fecha();
    e.proveedor = blank(r.proveedor());
    e.observaciones = blank(r.observaciones());
    e.items.clear();
    for (RepuestoItemRequest ri : r.items()) applyRepuestoItem(e, ri, null);
    recalcRepuesto(e);
    audit("Repuestos", "EDITAR", e.numero);
    return repuesto(e);
  }
  public void deleteRepuesto(UUID id) { PedidoRepuesto e = db.get(PedidoRepuesto.class, id); deleted(e); audit("Repuestos", "ELIMINAR", e.numero); }
  private void assertRepuestoEditable(PedidoRepuesto e) { if (e.estado == RepuestoPedidoState.COMPLETADO || e.estado == RepuestoPedidoState.CANCELADO) throw new BusinessException(409, "El pedido ya finalizó"); }
  private void applyRepuestoItem(PedidoRepuesto e, RepuestoItemRequest r, UUID id) {
    PedidoRepuestoItem i = new PedidoRepuestoItem();
    if (id != null) i.id = id;
    i.pedido = e;
    i.descripcion = r.descripcion().trim();
    i.tipo = r.tipo();
    i.cantidad = r.cantidad() == null ? BigDecimal.ONE : r.cantidad();
    if (i.cantidad.stripTrailingZeros().scale() > 0) throw new BusinessException(400, "La cantidad debe ser un número entero");
    i.precio = money(r.precio());
    i.subtotal = money(i.cantidad.multiply(i.precio));
    i.estado = r.estado() == null || r.estado().isBlank() ? RepuestoItemState.PENDIENTE_DE_PEDIR : RepuestoItemState.of(r.estado());
    i.observaciones = blank(r.observaciones());
    e.items.add(i);
  }
  private void recalcRepuesto(PedidoRepuesto e) { e.total = money(e.items.stream().map(x -> x.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)); }
  private RepuestoResponse repuesto(PedidoRepuesto e) {
    List<RepuestoItemResponse> items = e.items.stream().map(i -> new RepuestoItemResponse(i.id, i.descripcion, i.tipo, i.cantidad, i.precio, i.subtotal, i.estado.label(), i.observaciones)).toList();
    return new RepuestoResponse(e.id, e.numero, e.motovehiculo.id, e.motovehiculo.patente, e.cliente.id, e.cliente.nombre, e.ficha == null ? null : e.ficha.id, e.fecha, e.estado.label(), e.estadoPago.label(), e.total, e.proveedor, e.observaciones, items, e.createdAt);
  }
  public RepuestoResponse repuestoState(UUID id, StateRequest r) {
    PedidoRepuesto e = db.get(PedidoRepuesto.class, id);
    RepuestoPedidoState next = RepuestoPedidoState.of(r.estado());
    if (e.estado == RepuestoPedidoState.COMPLETADO || e.estado == RepuestoPedidoState.CANCELADO) throw new BusinessException(409, "El pedido ya finalizó");
    if (next == RepuestoPedidoState.CANCELADO) {
      if (e.items.stream().anyMatch(x -> x.estado == RepuestoItemState.ENTREGADO)) throw new BusinessException(409, "No puede cancelarse con ítems entregados");
    }
    e.estado = next;
    audit("Repuestos", "ESTADO", e.numero + " -> " + next.label());
    return repuesto(e);
  }
  public RepuestoResponse repuestoPago(UUID id, PagoRequest r) { PedidoRepuesto e = db.get(PedidoRepuesto.class, id); e.estadoPago = RepuestoPagoState.of(r.estadoPago()); audit("Repuestos", "PAGO", e.numero + " -> " + e.estadoPago.label()); return repuesto(e); }
  public RepuestoResponse repuestoItemState(UUID id, UUID itemId, StateRequest r) {
    PedidoRepuesto e = db.get(PedidoRepuesto.class, id); assertRepuestoEditable(e);
    PedidoRepuestoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    RepuestoItemState next = RepuestoItemState.of(r.estado());
    if (!validRepuestoItem(target.estado, next)) throw new BusinessException(422, "Transición de ítem inválida");
    target.estado = next;
    audit("Repuestos", "ITEM ESTADO", e.numero + " " + target.descripcion + " -> " + next.label());
    if (next == RepuestoItemState.ENTREGADO && e.estado == RepuestoPedidoState.EN_CURSO) {
      boolean allDone = !e.items.isEmpty() && e.items.stream().allMatch(x -> x.estado == RepuestoItemState.ENTREGADO || x.estado == RepuestoItemState.CANCELADO);
      if (allDone) e.estado = RepuestoPedidoState.COMPLETADO;
    }
    return repuesto(e);
  }
  public RepuestoResponse updateRepuestoItem(UUID id, UUID itemId, RepuestoItemRequest r) {
    PedidoRepuesto e = db.get(PedidoRepuesto.class, id); assertRepuestoEditable(e);
    PedidoRepuestoItem old = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    e.items.remove(old); applyRepuestoItem(e, r, itemId); recalcRepuesto(e);
    audit("Repuestos", "ÍTEM", e.numero); return repuesto(e);
  }
  public void deleteRepuestoItem(UUID id, UUID itemId) {
    PedidoRepuesto e = db.get(PedidoRepuesto.class, id); assertRepuestoEditable(e);
    PedidoRepuestoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    e.items.remove(target); recalcRepuesto(e); audit("Repuestos", "QUITAR ÍTEM", e.numero);
  }
  private boolean validRepuestoItem(RepuestoItemState from, RepuestoItemState next) {
    if (from == next) return false;
    if (from == RepuestoItemState.ENTREGADO || from == RepuestoItemState.CANCELADO) return false;
    if (next == RepuestoItemState.CANCELADO) return true;
    if (next == RepuestoItemState.ENTREGADO) return true;
    if (next == RepuestoItemState.RECIBIDO) return from == RepuestoItemState.PEDIDO || from == RepuestoItemState.PENDIENTE_DE_PEDIR;
    if (next == RepuestoItemState.PEDIDO) return from == RepuestoItemState.PENDIENTE_DE_PEDIR;
    return false;
  }

  // ---------- Controles de entrega ----------
  public List<ControlResponse> controls(boolean includeDeleted) {
    return db.all("select e from ControlEntrega e where 1=1" + active(includeDeleted) + " order by e.orden, e.nombre", ControlEntrega.class, Map.of()).stream().map(this::control).toList();
  }
  public ControlResponse createControl(ControlRequest r) {
    ControlEntrega e = new ControlEntrega();
    e.nombre = r.nombre().trim(); e.descripcion = blank(r.descripcion());
    e.obligatorio = r.obligatorio() == null || r.obligatorio();
    e.orden = r.orden() == null ? (int) db.count("select count(c) from ControlEntrega c where c.deletedAt is null", Map.of()) + 1 : r.orden();
    e.activo = r.activo() == null || r.activo();
    db.persist(e); audit("Controles", "CREAR", e.nombre); return control(e);
  }
  public ControlResponse updateControl(UUID id, ControlRequest r) {
    ControlEntrega e = db.get(ControlEntrega.class, id);
    e.nombre = r.nombre().trim(); e.descripcion = blank(r.descripcion());
    if (r.obligatorio() != null) e.obligatorio = r.obligatorio();
    if (r.orden() != null) e.orden = r.orden();
    if (r.activo() != null) e.activo = r.activo();
    audit("Control", "EDITAR", e.nombre); return control(e);
  }
  public void deleteControl(UUID id) { ControlEntrega e = db.get(ControlEntrega.class, id); if (db.count("select count(r) from RevisionControl r where r.control.id=:id", Map.of("id", id)) > 0) throw new BusinessException(409, "El control ya se usa en revisiones"); deleted(e); audit("Control", "ELIMINAR", e.nombre); }
  private ControlResponse control(ControlEntrega e) { return new ControlResponse(e.id, e.nombre, e.descripcion, e.obligatorio, e.orden, e.activo, e.createdAt, e.updatedAt); }

  // ---------- Revisión de entrega ----------
  public RevisionResponse revision(UUID fichaId) {
    Pedido f = db.get(Pedido.class, fichaId);
    RevisionEntrega r = db.one("select r from RevisionEntrega r where r.ficha.id=:f", RevisionEntrega.class, Map.of("f", fichaId));
    if (r == null || r.deletedAt != null) r = createRevision(f);
    return revisionDto(r);
  }
  public RevisionResponse updateRevisionControl(UUID fichaId, UUID itemId, RevisionControlRequest r) {
    RevisionEntrega rev = revisionEntity(fichaId);
    if (rev.estado == RevisionState.APROBADA) throw new BusinessException(409, "La revisión ya fue aprobada");
    RevisionControl c = rev.controles.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El control no existe"));
    String state = blank(r.estado());
    if (state != null) c.estado = RevisionControlState.of(state);
    c.observacion = blank(r.observacion());
    c.correccionNecesaria = blank(r.correccionNecesaria());
    if (c.estado != RevisionControlState.PENDIENTE) { c.revisadoPor = actor(); c.revisadoAt = Instant.now(); }
    audit("Revisión", "CONTROL", rev.ficha.numero + " " + c.control.nombre + " -> " + c.estado.label());
    return revisionDto(rev);
  }
  public RevisionResponse aprobarRevision(UUID fichaId, RevisionAprobarRequest r) {
    RevisionEntrega rev = revisionEntity(fichaId);
    if (rev.estado == RevisionState.APROBADA) throw new BusinessException(409, "La revisión ya fue aprobada");
    boolean pendOtorgar = rev.controles.stream().anyMatch(c -> c.control.obligatorio && c.estado == RevisionControlState.PENDIENTE);
    if (pendOtorgar && !r.forzada()) throw new BusinessException(422, "Faltan controles obligatorios por revisar");
    rev.estado = RevisionState.APROBADA; rev.aprobadoPor = actor(); rev.aprobadoAt = Instant.now(); rev.forzada = r.forzada(); rev.observacion = blank(r.observacion());
    Pedido ficha = rev.ficha;
    if (ficha.estado != FichaState.ENTREGADA && ficha.estado != FichaState.CANCELADA && ficha.estado != FichaState.PARA_ENTREGA) ficha.estado = FichaState.PARA_CONTROL;
    audit("Revisión", "APROBAR", rev.ficha.numero + (rev.forzada ? " (forzada)" : ""));
    return revisionDto(rev);
  }
  private RevisionEntrega revisionEntity(UUID fichaId) {
    RevisionEntrega r = db.one("SELECT r FROM RevisionEntrega r WHERE r.ficha.id=:f", RevisionEntrega.class, Map.of("f", fichaId));
    return r == null ? createRevision(db.get(Pedido.class, fichaId)) : r;
  }
  private RevisionEntrega createRevision(Pedido f) {
    RevisionEntrega rev = new RevisionEntrega(); rev.ficha = f; db.persist(rev);
    for (ControlEntrega c : db.all("select c from ControlEntrega c where c.deletedAt is null and c.activo=true order by c.orden", ControlEntrega.class, Map.of())) {
      RevisionControl rc = new RevisionControl(); rc.revision = rev; rc.control = c; rev.controles.add(rc); db.persist(rc);
    }
    return rev;
  }
  private RevisionResponse revisionDto(RevisionEntrega rev) {
    List<RevisionControlResponse> cs = rev.controles.stream().sorted(Comparator.comparingInt(x -> x.control.orden)).map(rc -> new RevisionControlResponse(rc.id, rc.control.id, rc.control.nombre, rc.control.obligatorio, rc.control.orden, rc.estado.label(), rc.observacion, rc.correccionNecesaria, rc.revisadoPor == null ? null : rc.revisadoPor.nombre, rc.revisadoAt)).toList();
    return new RevisionResponse(rev.id, rev.ficha.id, rev.ficha.numero, rev.estado.name(), rev.aprobadoPor == null ? null : rev.aprobadoPor.nombre, rev.aprobadoAt, rev.forzada, rev.observacion, cs);
  }

  // ---------- Configuración ----------
  public List<NamedResponse> brands(boolean includeDeleted) { return db.all("select e from MarcaMoto e where 1=1" + active(includeDeleted) + " order by e.nombre", MarcaMoto.class, Map.of()).stream().map(b -> new NamedResponse(b.id, b.nombre, b.activo, b.createdAt, b.updatedAt)).toList(); }
  public NamedResponse createBrand(NameRequest r) { MarcaMoto e = new MarcaMoto(); e.nombre = r.nombre().trim(); db.persist(e); audit("Configuración", "MARCAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public NamedResponse updateBrand(UUID id, NameRequest r) { MarcaMoto e = db.get(MarcaMoto.class, id); e.nombre = r.nombre().trim(); if (r.activo() != null) e.activo = r.activo(); audit("Configuración", "MARCAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public void deleteBrand(UUID id) { MarcaMoto e = db.get(MarcaMoto.class, id); if (db.count("select count(m) from Motovehiculo m where m.marca.id=:id", Map.of("id", id)) > 0) throw new BusinessException(409, "La marca tiene motos asociadas"); deleted(e); audit("Configuración", "MARCAS", "eliminar"); }
  public List<NamedResponse> categories(boolean includeDeleted) { return db.all("select e from CategoriaCatalogo e where 1=1" + active(includeDeleted) + " order by e.nombre", CategoriaCatalogo.class, Map.of()).stream().map(c -> new NamedResponse(c.id, c.nombre, c.activo, c.createdAt, c.updatedAt)).toList(); }
  public NamedResponse createCategory(NameRequest r) { CategoriaCatalogo e = new CategoriaCatalogo(); e.nombre = r.nombre().trim(); db.persist(e); audit("Configuración", "CATEGORÍAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public NamedResponse updateCategory(UUID id, NameRequest r) { CategoriaCatalogo e = db.get(CategoriaCatalogo.class, id); e.nombre = r.nombre().trim(); if (r.activo() != null) e.activo = r.activo(); audit("Configuración", "CATEGORÍAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public void deleteCategory(UUID id) { CategoriaCatalogo e = db.get(CategoriaCatalogo.class, id); if (db.count("SELECT 1 FROM ItemCatalogo i WHERE i.categoria.id=:id", Map.of("id", id)) > 0) throw new BusinessException(409, "La categoría tiene ítems asociados"); deleted(e); audit("Configuración", "CATEGORÍAS", "eliminar"); }
  public List<UserResponse> users(boolean includeDeleted) { return db.all("select e from AppUser e where 1=1" + active(includeDeleted) + " order by e.nombre", AppUser.class, Map.of()).stream().map(this::user).toList(); }
  public UserResponse user(AppUser e) { return new UserResponse(e.id, e.nombre, e.email, e.rol, e.activo, e.createdAt, e.updatedAt); }
  public UserResponse createUser(UserRequest r) {
    if (r.password() == null || r.password().isBlank()) throw new BusinessException(400, "La contraseña es obligatoria para crear usuarios");
    AppUser u = new AppUser(); u.username = r.username().trim().toLowerCase(); u.nombre = r.nombre().trim(); u.email = blank(r.email()); u.rol = r.rol();
    u.activo = r.activo() == null || r.activo(); u.passwordHash = encoder.encode(r.password());
    db.persist(u); audit("Configuración", "USUARIOS", u.username); return user(u);
  }
  public UserResponse updateUser(UUID id, UserRequest r) { AppUser u = db.get(AppUser.class, id); u.username = r.username().trim().toLowerCase(); u.nombre = r.nombre().trim(); u.email = blank(r.email()); u.rol = r.rol(); if (r.activo() != null) u.activo = r.activo(); if (r.password() != null && !r.password().isBlank()) u.passwordHash = encoder.encode(r.password()); audit("Configuración", "USUARIOS", u.username); return user(u); }
  public void deleteUser(UUID id) { AppUser u = db.get(AppUser.class, id); if (u.id.equals(actorId())) throw new BusinessException(409, "No puede eliminarse a sí mismo"); deleted(u); audit("Configuración", "USUARIOS", "eliminar"); }

  // ---------- Auditoría y reportes ----------
  public List<AuditResponse> audits(String q, UUID usuarioId, String modulo, String accion, Instant fechaDesde, Instant fechaHasta) {
    Map<String,Object> ps = p();
    String w = " where 1=1";
    if (q != null && !q.isBlank()) { w += " and (lower(e.descripcion) like :q or lower(e.accion) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (usuarioId != null) { w += " and e.usuario.id=:u"; ps.put("u", usuarioId); }
    if (modulo != null && !modulo.isBlank()) { w += " and lower(e.modulo)=lower(:mod)"; ps.put("mod", modulo); }
    if (accion != null && !accion.isBlank()) { w += " and lower(e.accion)=lower(:act)"; ps.put("act", accion); }
    if (fechaDesde != null) { w += " and e.fecha>=:desde"; ps.put("desde", fechaDesde); }
    if (fechaHasta != null) { w += " and e.fecha<=:hasta"; ps.put("hasta", fechaHasta); }
    w += " order by e.fecha desc";
    return db.all("select e from Auditoria e" + w, Auditoria.class, ps).stream().map(a -> new AuditResponse(a.id, a.fecha, a.usuario == null ? null : a.usuario.nombre, a.modulo, a.accion, a.descripcion)).toList();
  }
  private BigDecimal suma(String jpql, Map<String,Object> ps) { BigDecimal r = db.one(jpql, BigDecimal.class, ps); return money(r); }
  public List<ReportResponse> summary() {
    Map<String,Object> ps = Map.of("desde", today().minusDays(30));
    return List.of(
      new ReportResponse("Fichas de los últimos 30 días", BigDecimal.valueOf(db.count("select count(e) from Pedido e where e.deletedAt is null and e.fechaIngreso >= :desde", ps))),
      new ReportResponse("Presupuestado", suma("select coalesce(sum(e.total),0) from Pedido e where e.deletedAt is null", Map.of())),
      new ReportResponse("Facturado", suma("select coalesce(sum(e.total),0) from Pedido e where e.deletedAt is null and e.estadoPago='PAGADO'", Map.of())),
      new ReportResponse("En taller", suma("select coalesce(sum(e.total),0) from Pedido e where e.deletedAt is null and e.estado in ('INGRESADA','EN_TRABAJO','PARA_CONTROL')", Map.of()))
    );
  }
  public List<ReportResponse> evolution() {
    Map<YearMonth, BigDecimal> acc = new TreeMap<>();
    for (Object[] row : db.all("select e.fechaIngreso, e.total from Pedido e where e.deletedAt is null and e.fechaIngreso is not null", Object[].class, Map.of())) {
      YearMonth ym = YearMonth.from((LocalDate) row[0]);
      acc.merge(ym, money((BigDecimal) row[1]), BigDecimal::add);
    }
    return acc.entrySet().stream().map(e -> new ReportResponse(e.getKey().toString(), e.getValue())).toList();
  }
  public List<ReportResponse> topItems() {
    return db.all("SELECT i.descripcion, SUM(i.subtotal) FROM PedidoItem i GROUP BY i.descripcion ORDER BY SUM(i.subtotal) DESC", Object[].class, Map.of()).stream().limit(10).map(row -> new ReportResponse((String) row[0], money((BigDecimal) row[1]))).toList();
  }
  public DashboardResponse dashboard(LocalDate fechaDesde, LocalDate fechaHasta) {
    LocalDate hasta = fechaHasta == null ? today() : fechaHasta;
    LocalDate desde = fechaDesde == null ? today().minusDays(30) : fechaDesde;
    if (desde.isAfter(hasta)) throw new BusinessException(400, "Rango de fechas inválido");
    Map<String,Object> ps = Map.of("desde", desde, "hasta", hasta);
    String wPed = " WHERE e.deletedAt IS NULL AND e.fechaIngreso BETWEEN :desde AND :hasta";
    long pedidos = db.count("SELECT COUNT(e) FROM Pedido e" + wPed, ps);
    long enProceso = db.count("SELECT COUNT(e) FROM Pedido e" + wPed + " AND e.estado IN ('INGRESADA','EN_TRABAJO','PARA_CONTROL','PARA_ENTREGA')", ps);
    long aprobados = db.count("SELECT COUNT(r) FROM RevisionEntrega r JOIN r.ficha f WHERE r.estado='APROBADA' AND f.fechaIngreso BETWEEN :desde AND :hasta", ps);
    long cancelados = db.count("SELECT COUNT(e) FROM Pedido e" + wPed + " AND e.estado='CANCELADA'", ps);
    long pagados = db.count("SELECT COUNT(e) FROM Pedido e" + wPed + " AND e.estadoPago='PAGADO'", ps);
    BigDecimal presupuestado = suma("SELECT COALESCE(SUM(e.total),0) FROM Pedido e" + wPed, ps);
    BigDecimal facturado = suma("SELECT COALESCE(SUM(e.total),0) FROM Pedido e" + wPed + " AND e.estadoPago='PAGADO'", ps);
    List<DashboardDayResponse> evolucion = db.all("SELECT e.fechaIngreso, COALESCE(SUM(e.total),0) FROM Pedido e" + wPed + " GROUP BY e.fechaIngreso ORDER BY e.fechaIngreso", Object[].class, ps).stream().map(row -> new DashboardDayResponse((LocalDate) row[0], money((BigDecimal) row[1]))).toList();
    List<DashboardOrderResponse> recientes = db.list("SELECT e FROM Pedido e WHERE e.deletedAt IS NULL ORDER BY e.createdAt DESC", Pedido.class, Map.of(), 0, 12).stream().map(e -> new DashboardOrderResponse(e.id, e.numero, e.cliente.nombre, e.motovehiculo.marca.nombre + " " + e.motovehiculo.modelo, e.estado.name(), e.total, e.createdAt)).toList();
    return new DashboardResponse(desde, hasta, pedidos, enProceso, aprobados, pagados, cancelados, presupuestado, facturado, evolucion, recientes);
  }
}