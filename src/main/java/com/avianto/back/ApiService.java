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
  private void activoOnly(BaseEntity e) { if (e instanceof Cliente x) x.activo = false; if (e instanceof Motovehiculo x) x.activo = false; if (e instanceof ControlRevision x) x.activo = false; if (e instanceof MarcaMoto x) x.activo = false; if (e instanceof Categoria x) x.activo = false; if (e instanceof AppUser x) x.activo = false; }
  private void deleted(BaseEntity e) { activoOnly(e); e.deletedAt = Instant.now(); e.deletedBy = actorId(); }
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
    long motos = db.count("select count(distinct p.motovehiculo.id) from PropietarioMoto p where p.cliente.id=:id and p.deletedAt is null", Map.of("id", e.id));
    long fichas = db.count("select count(o) from Ficha o where o.cliente.id=:id and o.deletedAt is null", Map.of("id", e.id));
    return new ClientResponse(e.id, e.nombre, e.documento, e.telefono, e.email, e.direccion, e.observaciones, e.activo, motos, fichas, e.createdAt, e.updatedAt);
  }
  @Cacheable(value = "autocomplete", key = "'clients:' + #q") public List<AutocompleteResponse> clientAutocomplete(String q) {
    return db.all("select e from Cliente e where e.deletedAt is null and e.activo=true and (lower(e.nombre) like :q or lower(coalesce(e.documento,'')) like :q) order by e.nombre", Cliente.class, Map.of("q", "%" + q.toLowerCase() + "%")).stream().limit(15).map(e -> new AutocompleteResponse(e.id, e.nombre, e.documento)).toList();
  }
  @CacheEvict(value = "autocomplete", allEntries = true) void clearAutocomplete() {}

  // ---------- Motovehículos (propietario por historia, estado derivado de ficha activa) ----------
  public PageResponse<MotorcycleResponse> motorcycles(String q, UUID clientId, UUID brandId, String estado, Boolean activo, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (q != null && !q.isBlank()) { w += " and (lower(e.modelo) like :q or lower(e.patente) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (clientId != null) { w += " and e.id in (select o.motovehiculo.id from PropietarioMoto o where o.cliente.id=:c and o.deletedAt is null)"; ps.put("c", clientId); }
    if (brandId != null) { w += " and e.marca.id=:brand"; ps.put("brand", brandId); }
    if (estado != null && !estado.isBlank()) {
      if (estado.equalsIgnoreCase("En taller") || estado.equalsIgnoreCase("REPARACION")) w += " and e.id in (select f.motovehiculo.id from Ficha f where f.deletedAt is null and f.estado <> com.avianto.back.FichaState.ENTREGADA and f.estado <> com.avianto.back.FichaState.CANCELADA)";
      else {
        List<UUID> ids = motosEnEstado(FichaState.of(estado));
        if (ids.isEmpty()) return new PageResponse<>(List.of(), page, size, 0, 0, sortable(sort, Set.of("modelo", "patente", "kilometraje", "createdAt", "updatedAt"), "patente"), dirOf(dir));
        w += " and e.id in :ids"; ps.put("ids", ids);
      }
    }
    if (activo != null) { w += " and e.activo=:activo"; ps.put("activo", activo); }
    return page("from Motovehiculo e join e.marca", w, "from Motovehiculo e", ps, page, size, sortable(sort, Set.of("modelo", "patente", "kilometraje", "createdAt", "updatedAt"), "patente"), dir, x -> moto((Motovehiculo) x));
  }
  public MotorcycleResponse moto(UUID id) { return moto(db.get(Motovehiculo.class, id)); }
  public MotorcycleResponse createMotorcycle(MotorcycleRequest r) {
    Motovehiculo e = new Motovehiculo(); copy(r, e); db.persist(e);
    if (r.clienteId() != null) addOwner(e.id, new OwnerRequest(r.clienteId(), today(), null));
    audit("Motovehículos", "CREAR", e.patente); clearAutocomplete(); return moto(e);
  }
  public MotorcycleResponse updateMotorcycle(UUID id, MotorcycleRequest r) { Motovehiculo e = db.get(Motovehiculo.class, id); copy(r, e); audit("Motovehículos", "EDITAR", e.patente); clearAutocomplete(); return moto(e); }
  public void deleteMotorcycle(UUID id) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (db.count("select count(f) from Ficha f where f.motovehiculo.id=:id and f.deletedAt is null", Map.of("id", id)) > 0) throw new BusinessException(409, "La moto tiene fichas activas");
    deleted(e); audit("Motovehículos", "ELIMINAR", e.patente); clearAutocomplete();
  }
  public MotorcycleResponse updateMotoConfig(UUID id, MotoConfigServiceRequest r) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (r.kmServicePeriodo() != null) e.kmServicePeriodo = r.kmServicePeriodo();
    if (r.mesesServicePeriodo() != null) e.mesesServicePeriodo = r.mesesServicePeriodo();
    e.serviceObservaciones = blank(r.serviceObservaciones()); audit("Motovehículos", "CONFIG SERVICE", e.patente); return moto(e);
  }
  private void copy(MotorcycleRequest r, Motovehiculo e) {
    MarcaMoto b = db.get(MarcaMoto.class, r.marcaId());
    if (!b.activo || b.deletedAt != null) throw new BusinessException(409, "Marca inactiva");
    e.marca = b; e.modelo = r.modelo().trim(); e.patente = r.patente().trim().toUpperCase(); e.anio = r.anio(); e.kilometraje = r.kilometraje(); e.observaciones = blank(r.observaciones());
  }
  private MotorcycleResponse moto(Motovehiculo e) {
    PropietarioMoto o = propietarioActual(e.id);
    return new MotorcycleResponse(e.id, o == null ? null : o.cliente.id, o == null ? null : o.cliente.nombre, e.marca.id, e.marca.nombre, e.modelo, e.patente, e.anio, e.kilometraje, estadoMoto(e.id), e.kmUltimoService, e.fechaUltimoService, e.kmServicePeriodo, e.mesesServicePeriodo, e.serviceObservaciones, e.observaciones, e.activo, e.createdAt, e.updatedAt);
  }
  String estadoMoto(UUID motoId) {
    Ficha f = db.one("select f from Ficha f where f.motovehiculo.id=:moto and f.deletedAt is null order by f.fechaIngreso desc nulls last, f.createdAt desc", Ficha.class, Map.of("moto", motoId));
    return f == null ? "Disponible" : f.estado.label();
  }
  private List<UUID> motosEnEstado(FichaState estado) {
    Set<UUID> pick = new HashSet<>(); List<UUID> ids = new ArrayList<>();
    for (Ficha f : db.all("select f from Ficha f where f.deletedAt is null order by f.fechaIngreso desc nulls last, f.createdAt desc", Ficha.class, Map.of())) {
      if (!pick.add(f.motovehiculo.id)) continue;
      if (f.estado == estado) ids.add(f.motovehiculo.id);
    }
    return ids;
  }
  private PropietarioMoto propietarioActual(UUID motoId) { return db.one("select p from PropietarioMoto p where p.motovehiculo.id=:moto and p.fechaHasta is null and p.deletedAt is null", PropietarioMoto.class, Map.of("moto", motoId)); }
  @Cacheable(value = "autocomplete", key = "'motorcycles:' + #q") public List<AutocompleteResponse> motorcycleAutocomplete(String q) {
    return db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true and (lower(e.modelo) like lower(:q) or lower(e.patente) like lower(:q)) order by e.patente", Motovehiculo.class, Map.of("q", "%" + q.toLowerCase() + "%")).stream().limit(15).map(e -> new AutocompleteResponse(e.id, e.patente, e.marca.nombre + " " + e.modelo)).toList();
  }

  // ---------- Propietarios (toda la historia; actual = fechaHasta null) ----------
  public List<OwnerResponse> owners(UUID motoId) {
    return db.all("select e from PropietarioMoto e join e.cliente where e.motovehiculo.id=:moto and e.deletedAt is null order by e.fechaDesde desc", PropietarioMoto.class, Map.of("moto", motoId)).stream().map(this::owner).toList();
  }
  public OwnerResponse addOwner(UUID motoId, OwnerRequest r) {
    Motovehiculo m = db.get(Motovehiculo.class, motoId);
    Cliente c = db.get(Cliente.class, r.clienteId());
    if (!c.activo || c.deletedAt != null) throw new BusinessException(409, "Cliente inactivo");
    if (r.fechaDesde() == null && db.count("select count(p) from PropietarioMoto p where p.motovehiculo.id=:moto and p.fechaHasta is null and p.deletedAt is null", Map.of("moto", motoId)) > 0) throw new BusinessException(409, "La moto ya tiene un propietario actual; cerrá el período del actual primero");
    LocalDate inicio = r.fechaDesde() == null ? today() : r.fechaDesde();
    if (db.count("select count(p) from PropietarioMoto p where p.motovehiculo.id=:moto and p.cliente.id=:c and p.fechaHasta is null and p.deletedAt is null", Map.of("moto", motoId, "c", c.id)) > 0) throw new BusinessException(409, "El cliente ya es el propietario actual");
    PropietarioMoto n = new PropietarioMoto(); n.motovehiculo = m; n.cliente = c; n.fechaDesde = inicio; n.observaciones = blank(r.observaciones());
    db.persist(n);
    audit("Propietarios", "CAMBIAR", m.patente + " -> " + c.nombre);
    clearAutocomplete();
    return owner(n);
  }
  private OwnerResponse owner(PropietarioMoto e) { return new OwnerResponse(e.id, e.cliente.id, e.cliente.nombre, e.fechaDesde, e.fechaHasta, e.fechaHasta == null, e.observaciones); }

  // ---------- Service ----------
  public List<ServiceResponse> services(UUID motoId) {
    return db.all("select e from ServiceMoto e join e.motovehiculo left join e.ficha where e.motovehiculo.id=:moto order by e.fecha desc", ServiceMoto.class, Map.of("moto", motoId)).stream().map(this::service).toList();
  }
  public ServiceResponse addService(UUID motoId, ServiceRequest req) {
    if (req.kilometraje() == null || req.kilometraje() < 0) throw new BusinessException(400, "Kilometraje inválido");
    Motovehiculo m = db.get(Motovehiculo.class, motoId);
    if (m.kmUltimoService != null && req.kilometraje() < m.kmUltimoService) throw new BusinessException(409, "El kilometraje no puede ser menor al último service");
    ServiceMoto e = new ServiceMoto();
    e.motovehiculo = m; e.ficha = (req.fichaId() == null ? null : db.get(Ficha.class, req.fichaId()));
    e.kilometraje = req.kilometraje(); e.fecha = req.fecha() == null ? today() : req.fecha(); e.observaciones = blank(req.observaciones()); e.realizadoPor = actor();
    db.persist(e);
    m.kmUltimoService = e.kilometraje; m.fechaUltimoService = e.fecha;
    audit("Services", "REGISTRAR", m.patente + " km " + e.kilometraje);
    return service(e);
  }
  private ServiceResponse service(ServiceMoto e) { return new ServiceResponse(e.id, e.motovehiculo.id, e.ficha == null ? null : e.ficha.id, e.ficha == null ? null : e.ficha.numero, e.kilometraje, e.fecha, e.observaciones, e.realizadoPor == null ? null : e.realizadoPor.nombre, e.createdAt); }
  public List<NextServiceResponse> nextServices() {
    LocalDate h = today();
    return db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true order by e.patente", Motovehiculo.class, Map.of()).stream().map(m -> {
PropietarioMoto o = propietarioActual(m.id);
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
      return new NextServiceResponse(m.id, m.patente, o == null ? null : o.cliente.nombre, m.marca.nombre + " " + m.modelo, m.kilometraje, m.kmUltimoService, m.fechaUltimoService, m.kmServicePeriodo, m.mesesServicePeriodo, proxKm, kmFaltan, proxFecha, diasFaltan, atrasadoKm, atrasadoFecha, sinRef);
    }).toList();
  }

  // ---------- Fichas (solo trabajos cobrables) ----------
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
    return page("from Ficha e join e.cliente join e.motovehiculo", w, "from Ficha e", ps, page, size, sortable(sort, Set.of("createdAt", "fechaIngreso", "estado", "total", "numero"), "fechaIngreso"), dir, x -> ficha((Ficha) x));
  }
  public FichaResponse ficha(UUID id) { return ficha(db.get(Ficha.class, id)); }
  public FichaResponse createFicha(FichaRequest r) {
    Ficha e = new Ficha(); copy(r, e);
    e.numero = "F-" + System.currentTimeMillis();
    db.persist(e);
    audit("Fichas", "CREAR", e.numero);
    return ficha(e);
  }
  public FichaResponse updateFicha(UUID id, FichaRequest r) { Ficha e = db.get(Ficha.class, id); assertEditable(e); e.trabajos.clear(); copy(r, e); audit("Fichas", "EDITAR", e.numero); return ficha(e); }
  public void deleteFicha(UUID id) { Ficha e = db.get(Ficha.class, id); if (e.estado == FichaState.ENTREGADA) throw new BusinessException(409, "No puede eliminarse una ficha entregada"); deleted(e); audit("Fichas", "ELIMINAR", e.numero); }
  public FichaResponse fichaState(UUID id, StateRequest r) { Ficha e = db.get(Ficha.class, id); e.estado = FichaState.of(r.estado()); audit("Fichas", "ESTADO", e.numero + " -> " + e.estado.label()); return ficha(e); }
  public FichaResponse fichaPago(UUID id, PagoRequest r) { Ficha e = db.get(Ficha.class, id); e.estadoPago = PagoState.of(r.estadoPago()); if (e.estadoPago == PagoState.PAGADO && e.fechaEntregaReal == null) e.fechaEntregaReal = today(); audit("Fichas", "PAGO", e.numero + " -> " + e.estadoPago.label()); return ficha(e); }
  private void assertEditable(Ficha e) { if (e.estado == FichaState.ENTREGADA || e.estado == FichaState.CANCELADA) throw new BusinessException(409, "La ficha ya finalizó"); }
  private void copy(FichaRequest r, Ficha e) {
    Cliente c = db.get(Cliente.class, r.clienteId());
    Motovehiculo m = db.get(Motovehiculo.class, r.motoId());
    if (!c.activo || !m.activo || c.deletedAt != null || m.deletedAt != null) throw new BusinessException(409, "Cliente o moto inactivo");
    e.cliente = c; e.motovehiculo = m;
    e.fechaIngreso = r.fechaIngreso() == null ? today() : r.fechaIngreso();
    e.fechaEntregaEstimada = r.fechaEntregaEstimada();
    e.kilometrajeIngreso = r.kilometrajeIngreso();
    e.vencimiento = r.vencimiento();
    e.observaciones = blank(r.observaciones());
    e.descuentoGlobal = money(r.descuentoGlobal());
    e.iva = r.iva();
    BigDecimal sum = BigDecimal.ZERO;
    if (r.trabajos() != null) for (FichaTrabajoRequest pos : r.trabajos()) { applyTrabajo(e, pos, null); sum = sum.add(e.trabajos.get(e.trabajos.size() - 1).subtotal); }
    e.total = money(e.iva ? sum.add(e.descuentoGlobal) : sum).add(BigDecimal.ZERO);
    recalc(e);
  }
  private void applyTrabajo(Ficha e, FichaTrabajoRequest r, UUID id) {
    FichaTrabajo t = new FichaTrabajo();
    if (id != null) t.id = id;
    t.ficha = e;
    t.descripcion = r.descripcion().trim();
    t.precioAplicado = money(r.precioUnitario());
    t.descuento = money(r.descuento());
    t.subtotal = money(t.precioAplicado.subtract(t.descuento));
    if (t.subtotal.signum() < 0) throw new BusinessException(400, "Descuento de línea inválido");
    t.estadoTrabajo = (r.estadoTrabajo() == null || r.estadoTrabajo().isBlank()) ? TrabajoState.PENDIENTE : TrabajoState.of(r.estadoTrabajo());
    t.observacionTrabajo = blank(r.observacionTrabajo());
    if (t.estadoTrabajo == TrabajoState.REALIZADO) { t.completadoAt = Instant.now(); t.completadoPor = actorId(); }
    e.trabajos.add(t);
  }
  private void recalc(Ficha e) {
    BigDecimal sum = e.trabajos.stream().map(x -> x.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    sum = sum.subtract(e.descuentoGlobal);
    if (sum.signum() < 0) throw new BusinessException(400, "Descuento global inválido");
    e.total = money(e.iva ? sum.multiply(new BigDecimal("1.21")) : sum);
  }
  private FichaResponse ficha(Ficha e) {
    List<FichaTrabajoResponse> lines = e.trabajos.stream().map(t -> new FichaTrabajoResponse(t.id, t.descripcion, t.precioAplicado, t.descuento, t.subtotal, t.estadoTrabajo.label(), t.observacionTrabajo, t.completadoAt, t.completadoPor)).toList();
    List<PhotoResponse> fotos = e.fotos.stream().map(f -> photo(e.id, f)).toList();
    return new FichaResponse(e.id, e.numero, e.cliente.id, e.motovehiculo.id, e.cliente.nombre, e.motovehiculo.marca.nombre + " " + e.motovehiculo.modelo, e.motovehiculo.patente, e.vencimiento, e.fechaIngreso, e.fechaEntregaEstimada, e.fechaEntregaReal, e.kilometrajeIngreso, e.observaciones, e.descuentoGlobal, e.iva, e.estado.label(), e.estadoPago.label(), e.total, e.createdAt, lines, fotos);
  }
  public FichaResponse addTrabajo(UUID id, FichaTrabajoRequest r) { Ficha e = db.get(Ficha.class, id); assertEditable(e); applyTrabajo(e, r, null); recalc(e); if (e.estado == FichaState.CARGA) e.estado = FichaState.EN_PROCESO; audit("Fichas", "TRABAJO", e.numero); return ficha(e); }
  public FichaResponse updateTrabajo(UUID id, UUID trabajoId, FichaTrabajoRequest r) {
    Ficha e = db.get(Ficha.class, id); assertEditable(e);
    FichaTrabajo old = e.trabajos.stream().filter(x -> x.id.equals(trabajoId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    e.trabajos.remove(old); applyTrabajo(e, r, trabajoId); recalc(e);
    audit("Fichas", "TRABAJO", e.numero); return ficha(e);
  }
  public void deleteTrabajo(UUID id, UUID trabajoId) {
    Ficha e = db.get(Ficha.class, id); assertEditable(e);
    FichaTrabajo target = e.trabajos.stream().filter(x -> x.id.equals(trabajoId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    e.trabajos.remove(target); recalc(e); audit("Fichas", "QUITAR TRABAJO", e.numero);
  }
  public FichaResponse trabajoState(UUID id, UUID trabajoId, StateRequest r) {
    Ficha e = db.get(Ficha.class, id);
    FichaTrabajo target = e.trabajos.stream().filter(x -> x.id.equals(trabajoId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    TrabajoState next = TrabajoState.of(r.estado());
    if (target.estadoTrabajo == TrabajoState.REALIZADO || target.estadoTrabajo == TrabajoState.CANCELADO) throw new BusinessException(422, "Trabajo finalizado");
    target.estadoTrabajo = next;
    if (next == TrabajoState.REALIZADO) { target.completadoAt = Instant.now(); target.completadoPor = actorId(); }
    audit("Fichas", "TRABAJO ESTADO", e.numero + " " + target.descripcion + " -> " + next.label());
    if (next == TrabajoState.REALIZADO && (e.estado == FichaState.CARGA || e.estado == FichaState.EN_PROCESO) && !e.trabajos.isEmpty() && e.trabajos.stream().allMatch(x -> x.estadoTrabajo == TrabajoState.REALIZADO)) e.estado = FichaState.REVISION;
    return ficha(e);
  }

  // ---------- Fotos ----------
  public PhotoResponse createPhoto(UUID id, PhotoRequest r) {
    Ficha e = db.get(Ficha.class, id);
    byte[] data;
    try { data = Base64.getDecoder().decode(r.base64()); } catch (IllegalArgumentException ex) { throw new BusinessException(400, "base64 inválido"); }
    if (data.length > 5_000_000) throw new BusinessException(400, "Foto excede 5 MB");
    if (!"image/webp".equalsIgnoreCase(r.contentType()) || !webp(data)) throw new BusinessException(400, "La foto debe estar en formato WebP");
    FichaFoto f = new FichaFoto();
    f.ficha = e;
    f.filename = r.filename().replaceAll("[^a-zA-Z0-9._-]", "_").replaceFirst("(?i)\\.[^.]+$", "") + ".webp";
    f.contentType = "image/webp"; f.content = data;
    db.persist(f);
    audit("Fichas", "FOTO", e.numero);
    return photo(e.id, f);
  }
  public FichaFoto photo(UUID fichaId, UUID photoId) {
    FichaFoto f = db.one("select f from FichaFoto f where f.ficha.id=:ficha and f.id=:photo", FichaFoto.class, Map.of("ficha", fichaId, "photo", photoId));
    if (f == null) throw new NotFoundException("Imagen inexistente");
    return f;
  }
  private static boolean webp(byte[] data) { return data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P'; }
  private PhotoResponse photo(UUID fichaId, FichaFoto f) { return new PhotoResponse(f.id, f.filename, f.contentType, f.createdAt, "/fichas/" + fichaId + "/fotos/" + f.id); }

  // ---------- Pedidos de repuestos (piezas y accesorios) ----------
  public PageResponse<RepuestoResponse> repuestos(String estado, String estadoPago, UUID motoId, UUID clienteId, String q, LocalDate desde, LocalDate hasta, boolean deleted, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(deleted);
    if (estado != null && !estado.isBlank()) { w += " and e.estado=:s"; ps.put("s", RepuestoPedidoState.of(estado)); }
    if (estadoPago != null && !estadoPago.isBlank()) { w += " and e.estadoPago=:p"; ps.put("p", PagoState.of(estadoPago)); }
    if (motoId != null) { w += " and e.motovehiculo.id=:m"; ps.put("m", motoId); }
    if (clienteId != null) { w += " and e.cliente.id=:c"; ps.put("c", clienteId); }
    if (q != null && !q.isBlank()) { w += " and (lower(e.numero) like :q or lower(coalesce(e.proveedor,'')) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (desde != null) { w += " and e.fecha>=:desde"; ps.put("desde", desde); }
    if (hasta != null) { w += " and e.fecha<=:hasta"; ps.put("hasta", hasta); }
    return page("from RepuestoPedido e join e.motovehiculo join e.cliente", w, "from RepuestoPedido e", ps, page, size, sortable(sort, Set.of("fecha", "createdAt", "total", "estado"), "fecha"), dir, x -> repuesto((RepuestoPedido) x));
  }
  public RepuestoResponse repuesto(UUID id) { return repuesto(db.get(RepuestoPedido.class, id)); }
  public RepuestoResponse createRepuesto(RepuestoRequest r) {
    if (r.items() == null || r.items().isEmpty()) throw new BusinessException(400, "El pedido debe tener al menos un ítem");
    Motovehiculo m = db.get(Motovehiculo.class, r.motoVehiculoId());
    Cliente c = db.get(Cliente.class, r.clienteId());
    RepuestoPedido e = new RepuestoPedido();
    e.motovehiculo = m; e.cliente = c;
    e.ficha = r.fichaId() == null ? null : db.get(Ficha.class, r.fichaId());
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
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    if (r.items() == null || r.items().isEmpty()) throw new BusinessException(400, "El pedido debe tener al menos un ítem");
    if (r.fichaId() != null) e.ficha = db.get(Ficha.class, r.fichaId());
    e.fecha = r.fecha() == null ? today() : r.fecha();
    e.proveedor = blank(r.proveedor()); e.observaciones = blank(r.observaciones());
    e.items.clear();
    for (RepuestoItemRequest req : r.items()) applyRepuestoItem(e, req, null);
    recalcRepuesto(e);
    audit("REPUESTOS", "EDITAR", e.numero);
    return repuesto(e);
  }
  public void deleteRepuesto(UUID id) { RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e); deleted(e); audit("REPUESTOS", "ELIMINAR", e.numero); }
  private void assertRepuestoEditable(RepuestoPedido e) { if (e.estado == RepuestoPedidoState.COMPLETADO || e.estado == RepuestoPedidoState.CANCELADO) throw new BusinessException(409, "El pedido ya finalizó"); }
  private void applyRepuestoItem(RepuestoPedido e, RepuestoItemRequest r, UUID id) {
    RepuestoPedidoItem i = new RepuestoPedidoItem();
    if (id != null) i.id = id;
    i.pedido = e;
    if (r.fichaTrabajoId() != null) i.fichaTrabajo = db.get(FichaTrabajo.class, r.fichaTrabajoId());
    i.descripcion = r.descripcion().trim(); i.tipo = r.tipo(); i.cantidad = r.cantidad() == null ? BigDecimal.ONE : r.cantidad(); i.precio = money(r.precio());
    i.subtotal = money(i.cantidad.multiply(i.precio));
    i.estado = r.estado() == null || r.estado().isBlank() ? RepuestoItemState.PENDIENTE_DE_PEDIR : RepuestoItemState.of(r.estado());
    i.observaciones = blank(r.observaciones());
    e.items.add(i);
  }
  private void recalcRepuesto(RepuestoPedido e) { e.total = money(e.items.stream().map(x -> x.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)); }
  private RepuestoResponse repuesto(RepuestoPedido e) {
    List<RepuestoItemResponse> items = e.items.stream().map(i -> new RepuestoItemResponse(i.id, i.fichaTrabajo == null ? null : i.fichaTrabajo.id, i.descripcion, i.tipo, i.cantidad, i.precio, i.subtotal, i.estado.label(), i.observaciones)).toList();
    return new RepuestoResponse(e.id, e.numero, e.motovehiculo.id, e.motovehiculo.patente, e.cliente.id, e.cliente.nombre, e.ficha == null ? null : e.ficha.id, e.fecha, e.estado.label(), e.estadoPago.label(), e.total, e.proveedor, e.observaciones, items, e.createdAt);
  }
  public RepuestoResponse repuestoEstado(UUID id, StateRequest r) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    e.estado = RepuestoPedidoState.of(r.estado());
    audit("REPUESTOS", "ESTADO", e.numero + " -> " + e.estado.label()); return repuesto(e);
  }
  public RepuestoResponse repuestoPago(UUID id, PagoRequest r) { RepuestoPedido e = db.get(RepuestoPedido.class, id); e.estadoPago = PagoState.of(r.estadoPago()); audit("REPUESTOS", "PAGO", e.numero + " -> " + e.estadoPago.label()); return repuesto(e); }
  public RepuestoResponse repuestoItemEstado(UUID id, UUID itemId, StateRequest r) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    RepuestoItemState next = RepuestoItemState.of(r.estado());
    if (target.estado == RepuestoItemState.ENTREGADO || target.estado == RepuestoItemState.CANCELADO) throw new BusinessException(422, "Ítem finalizado");
    target.estado = next;
    audit("REPUESTOS", "ITEM ESTADO", e.numero + " " + target.descripcion + " -> " + next.label());
    if (next == RepuestoItemState.ENTREGADO && e.estado == RepuestoPedidoState.EN_CURSO && e.items.stream().allMatch(x -> x.estado == RepuestoItemState.ENTREGADO || x.estado == RepuestoItemState.CANCELADO)) e.estado = RepuestoPedidoState.COMPLETADO;
    return repuesto(e);
  }
  public RepuestoResponse updateRepuestoItem(UUID id, UUID itemId, RepuestoItemRequest r) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoItem old = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    e.items.remove(old); applyRepuestoItem(e, r, itemId); recalcRepuesto(e);
    audit("REPUESTOS", "ÍTEM", e.numero); return repuesto(e);
  }
  public void deleteRepuestoItem(UUID id, UUID itemId) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    e.items.remove(target); recalcRepuesto(e); audit("REPUESTOS", "QUITAR ÍTEM", e.numero);
  }

  // ---------- Control de revisión (catálogo con categorías N:M) ----------
  public Categoria categoria(UUID id) { return db.get(Categoria.class, id); }
  public List<ControlResponse> controls(boolean includeDeleted) {
    return db.all("select e from ControlRevision e where 1=1" + active(includeDeleted) + " order by e.orden, e.nombre", ControlRevision.class, Map.of()).stream().map(this::control).toList();
  }
  public ControlResponse createControl(ControlRequest r) {
    ControlRevision e = new ControlRevision();
    e.nombre = r.nombre().trim(); e.descripcion = blank(r.descripcion());
    e.obligatorio = r.obligatorio() == null || r.obligatorio();
    e.orden = r.orden() == null ? (int) db.count("select count(c) from ControlRevision c where c.deletedAt is null", Map.of()) + 1 : r.orden();
    e.activo = r.activo() == null || r.activo();
    applyCategorias(e, r.categoriaIds());
    db.persist(e); audit("CONTROLES", "CREAR", e.nombre); return control(e);
  }
  public ControlResponse updateControl(UUID id, ControlRequest r) {
    ControlRevision e = db.get(ControlRevision.class, id);
    e.nombre = r.nombre().trim(); e.descripcion = blank(r.descripcion());
    if (r.obligatorio() != null) e.obligatorio = r.obligatorio();
    if (r.orden() != null) e.orden = r.orden();
    if (r.activo() != null) e.activo = r.activo();
    applyCategorias(e, r.categoriaIds());
    audit("CONTROLES", "EDITAR", e.nombre); return control(e);
  }
  public void deleteControl(UUID id) { ControlRevision e = db.get(ControlRevision.class, id); if (db.count("select count(r) from RevisionControl r where r.control.id=:id", Map.of("id", id)) > 0) throw new BusinessException(409, "El control ya se usa en revisiones"); deleted(e); audit("CONTROLES", "ELIMINAR", e.nombre); }
  private void applyCategorias(ControlRevision e, List<UUID> ids) {
    e.categorias.clear();
    if (ids != null) for (UUID cid : ids) e.categorias.add(db.get(Categoria.class, cid));
  }
  private ControlResponse control(ControlRevision e) {
    List<NamedResponse> cats = e.categorias.stream().map(c -> new NamedResponse(c.id, c.nombre, c.activo, c.createdAt, c.updatedAt)).toList();
    return new ControlResponse(e.id, e.nombre, e.descripcion, e.obligatorio, e.orden, e.activo, cats, e.createdAt, e.updatedAt);
  }

  // ---------- Revisión final de entrega ----------
  public RevisionResponse revision(UUID fichaId) {
    Ficha f = db.get(Ficha.class, fichaId);
    Revision r = db.one("select r from Revision r where r.ficha.id=:f", Revision.class, Map.of("f", fichaId));
    if (r == null || r.deletedAt != null) r = createRevision(f);
    return revisionDto(r);
  }
  public RevisionResponse updateControlEstado(UUID fichaId, UUID controlId, RevisionControlRequest r) {
    Revision rev = revisionEntity(fichaId);
    if (rev.estado == RevisionState.APROBADA) throw new BusinessException(409, "La revisión ya fue aprobada");
    RevisionControl c = rev.controles.stream().filter(x -> x.id.equals(controlId)).findFirst().orElseThrow(() -> new NotFoundException("El control no existe"));
    String state = blank(r.estado());
    if (state != null) c.estado = RevisionControlState.of(state);
    c.observacion = blank(r.observacion());
    c.correccionNecesaria = blank(r.correccionNecesaria());
    if (c.estado != RevisionControlState.PENDIENTE) { c.revisadoPor = actor(); c.revisadoAt = Instant.now(); }
    audit("REVISION", "CONTROL", rev.ficha.numero + " " + c.control.nombre + " -> " + c.estado.label());
    return revisionDto(rev);
  }
  public RevisionResponse aprobarRevision(UUID fichaId, RevisionAprobarRequest r) {
    Revision rev = revisionEntity(fichaId);
    if (rev.estado == RevisionState.APROBADA) throw new BusinessException(409, "La revisión ya fue aprobada");
    boolean pendOblig = rev.controles.stream().anyMatch(c -> c.control.obligatorio && c.estado == RevisionControlState.PENDIENTE);
    if (pendOblig && !r.forzada()) throw new BusinessException(422, "Faltan controles obligatorios por revisar");
    rev.estado = RevisionState.APROBADA; rev.aprobadoPor = actor(); rev.aprobadoAt = Instant.now(); rev.forzada = r.forzada(); rev.observacion = blank(r.observacion());
    Ficha f = rev.ficha;
    if (f.estado != FichaState.ENTREGADA && f.estado != FichaState.CANCELADA) { f.estado = FichaState.ENTREGADA; }
    if (f.fechaEntregaReal == null) f.fechaEntregaReal = today();
    audit("REVISION", "APROBAR", f.numero + (rev.forzada ? " (forzada)" : ""));
    return revisionDto(rev);
  }
  private Revision revisionEntity(UUID fichaId) {
    Revision r = db.one("SELECT r FROM Revision r WHERE r.ficha.id=:f", Revision.class, Map.of("f", fichaId));
    return r == null ? createRevision(db.get(Ficha.class, fichaId)) : r;
  }
  private Revision createRevision(Ficha f) {
    Revision rev = new Revision(); rev.ficha = f; db.persist(rev);
    for (ControlRevision c : db.all("select c from ControlRevision c where c.deletedAt is null and c.activo=true order by c.orden", ControlRevision.class, Map.of())) {
      RevisionControl rc = new RevisionControl(); rc.revision = rev; rc.control = c; rev.controles.add(rc); db.persist(rc);
    }
    return rev;
  }
  private RevisionResponse revisionDto(Revision rev) {
    List<RevisionControlResponse> cs = rev.controles.stream().sorted(Comparator.comparingInt(x -> x.control.orden)).map(rc -> new RevisionControlResponse(rc.id, rc.control.id, rc.control.nombre, rc.control.categorias.stream().map(c -> c.nombre).collect(Collectors.joining(", ")), rc.control.obligatorio, rc.control.orden, rc.estado.label(), rc.observacion, rc.correccionNecesaria, rc.revisadoPor == null ? null : rc.revisadoPor.nombre, rc.revisadoAt)).toList();
    return new RevisionResponse(rev.id, rev.ficha.id, rev.ficha.numero, rev.estado.name(), rev.aprobadoPor == null ? null : rev.aprobadoPor.nombre, rev.aprobadoAt, rev.forzada, rev.observacion, cs);
  }

  // ---------- Configuración ----------
  public List<NamedResponse> brands(boolean includeDeleted) { return db.all("select e from MarcaMoto e where 1=1" + active(includeDeleted) + " order by e.nombre", MarcaMoto.class, Map.of()).stream().map(b -> new NamedResponse(b.id, b.nombre, b.activo, b.createdAt, b.updatedAt)).toList(); }
  public NamedResponse createBrand(NameRequest r) { MarcaMoto e = new MarcaMoto(); e.nombre = r.nombre().trim(); db.persist(e); audit("CONFIG", "MARCAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public NamedResponse updateBrand(UUID id, NameRequest r) { MarcaMoto e = db.get(MarcaMoto.class, id); e.nombre = r.nombre().trim(); if (r.activo() != null) e.activo = r.activo(); audit("CONFIG", "MARCAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public void deleteBrand(UUID id) { MarcaMoto e = db.get(MarcaMoto.class, id); deleted(e); audit("CONFIG", "MARCAS", "eliminar"); }
  public List<NamedResponse> categorias(boolean includeDeleted) { return db.all("select e from Categoria e where 1=1" + active(includeDeleted) + " order by e.nombre", Categoria.class, Map.of()).stream().map(c -> new NamedResponse(c.id, c.nombre, c.activo, c.createdAt, c.updatedAt)).toList(); }
  public NamedResponse createCategoria(NameRequest r) { Categoria e = new Categoria(); e.nombre = r.nombre().trim(); db.persist(e); audit("CONFIG", "CATEGORÍAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public NamedResponse updateCategoria(UUID id, NameRequest r) { Categoria e = db.get(Categoria.class, id); e.nombre = r.nombre().trim(); if (r.activo() != null) e.activo = r.activo(); audit("CONFIG", "CATEGORÍAS", e.nombre); return new NamedResponse(e.id, e.nombre, e.activo, e.createdAt, e.updatedAt); }
  public void deleteCategoria(UUID id) { Categoria e = db.get(Categoria.class, id); if (db.count("select count(r) from ControlRevision c join c.categorias cat where cat.id=:id", Map.of("id", id)) > 0) throw new BusinessException(409, "La categoría está en uso por controles"); deleted(e); audit("CONFIG", "CATEGORÍAS", "eliminar"); }
  public List<UserResponse> users(boolean includeDeleted) { return db.all("select e from AppUser e where 1=1" + active(includeDeleted) + " order by e.nombre", AppUser.class, Map.of()).stream().map(this::user).toList(); }
  public UserResponse user(AppUser e) { return new UserResponse(e.id, e.nombre, e.email, e.rol, e.activo, e.createdAt, e.updatedAt); }
  public UserResponse createUser(UserRequest r) {
    if (r.password() == null || r.password().isBlank()) throw new BusinessException(400, "La contraseña es obligatoria para crear usuarios");
    AppUser u = new AppUser(); u.username = r.username().trim().toLowerCase(); u.nombre = r.nombre().trim(); u.email = blank(r.email()); u.rol = r.rol();
    u.activo = r.activo() == null || r.activo(); u.passwordHash = encoder.encode(r.password());
    db.persist(u); audit("CONFIG", "USERS", u.username); return user(u);
  }
  public UserResponse updateUser(UUID id, UserRequest r) { AppUser u = db.get(AppUser.class, id); u.username = r.username().trim().toLowerCase(); u.nombre = r.nombre().trim(); u.email = blank(r.email()); u.rol = r.rol(); if (r.activo() != null) u.activo = r.activo(); if (r.password() != null && !r.password().isBlank()) u.passwordHash = encoder.encode(r.password()); audit("CONFIG", "USERS", u.username); return user(u); }
  public void deleteUser(UUID id) { AppUser u = db.get(AppUser.class, id); if (u.id.equals(actorId())) throw new BusinessException(409, "No puede eliminarse a sí mismo"); deleted(u); audit("CONFIG", "USERS", "eliminar"); }

  // ---------- Auditoría / reportes / dashboard ----------
  public List<AuditResponse> audits(String q, UUID registrarId, String modulo, String accion, Instant desde, Instant hasta) {
    Map<String,Object> ps = p();
    String w = " where 1=1";
    if (q != null && !q.isBlank()) { w += " and (lower(e.descripcion) like :q or lower(e.accion) like :q)"; ps.put("q", "%" + q.toLowerCase() + "%"); }
    if (registrarId != null) { w += " and e.usuario.id=:u"; ps.put("u", registrarId); }
    if (modulo != null && !modulo.isBlank()) { w += " and lower(e.modulo) like lower(concat('%',:mod,'%'))"; ps.put("mod", modulo); }
    if (accion != null && !accion.isBlank()) { w += " and lower(e.accion) like lower(concat('%',:act,'%'))"; ps.put("act", accion); }
    if (desde != null) { w += " and e.fecha>=:desde"; ps.put("desde", desde); }
    if (hasta != null) { w += " and e.fecha<=:hasta"; ps.put("hasta", hasta); }
    w += " order by e.fecha desc";
    return db.all("select e from Auditoria e" + w, Auditoria.class, ps).stream().map(a -> new AuditResponse(a.id, a.fecha, a.usuario == null ? null : a.usuario.nombre, a.modulo, a.accion, a.descripcion)).toList();
  }
  private BigDecimal suma(String jpql, Map<String,Object> ps) { return money(db.one(jpql, BigDecimal.class, ps)); }
  public List<ReportResponse> summary() {
    Map<String,Object> ps = Map.of("desde", today().minusDays(30));
    return List.of(
      new ReportResponse("Fichas último mes", BigDecimal.valueOf(db.count("select count(e) from Ficha e where e.deletedAt is null and e.fechaIngreso >= :desde", ps))),
      new ReportResponse("Próximos services", BigDecimal.valueOf(db.count("select count(e) from Motovehiculo e where e.deletedAt is null", Map.of()))),
      new ReportResponse("En proceso", BigDecimal.valueOf(db.count("select count(e) from Ficha e where e.deletedAt is null and e.estado in ('CARGA','EN_PROCESO','REVISION')", Map.of())))
    );
  }
  public List<ReportResponse> evolution() {
    Map<YearMonth, BigDecimal> acc = new TreeMap<>();
    for (Object[] row : db.all("select e.fechaIngreso, e.total from Ficha e where e.deletedAt is null and e.fechaIngreso is not null", Object[].class, Map.of())) {
      YearMonth ym = YearMonth.from((LocalDate) row[0]);
      acc.merge(ym, money((BigDecimal) row[1]), BigDecimal::add);
    }
    return acc.entrySet().stream().map(e -> new ReportResponse(e.getKey().toString(), e.getValue())).toList();
  }
  public DashboardResponse dashboard(LocalDate fechaDesde, LocalDate fechaHasta) {
    LocalDate hasta = fechaHasta == null ? today() : fechaHasta;
    LocalDate desde = fechaDesde == null ? today().minusDays(30) : fechaDesde;
    if (desde.isAfter(hasta)) throw new BusinessException(400, "Rango de fechas inválido");
    Map<String,Object> ps = Map.of("desde", desde, "hasta", hasta);
    String wFicha = " WHERE e.deletedAt IS NULL AND e.fechaIngreso BETWEEN :desde AND :hasta";
    long fichas = db.count("SELECT COUNT(e) FROM Ficha e" + wFicha, ps);
    List<DashboardOrderResponse> recientes = db.list("SELECT e FROM Ficha e" + wFicha + " ORDER BY e.fechaIngreso DESC, e.createdAt DESC", Ficha.class, ps, 0, 12).stream().map(e -> new DashboardOrderResponse(e.id, e.numero, e.cliente.nombre, e.motovehiculo.marca.nombre + " " + e.motovehiculo.modelo, e.estado.label(), e.total, e.createdAt)).toList();
    return new DashboardResponse(desde, hasta, fichas, recientes);
  }
  public TallerResponse taller() {
    Map<FichaState, List<TallerMotoResponse>> buckets = new EnumMap<>(FichaState.class);
    Set<UUID> pick = new HashSet<>();
    for (Ficha f : db.all("select f from Ficha f where f.deletedAt is null order by f.fechaIngreso desc nulls last, f.createdAt desc", Ficha.class, Map.of())) {
      if (!pick.add(f.motovehiculo.id)) continue;
      buckets.computeIfAbsent(f.estado, k -> new ArrayList<>()).add(new TallerMotoResponse(f.motovehiculo.id, f.motovehiculo.patente, f.motovehiculo.marca.nombre + " " + f.motovehiculo.modelo, f.cliente.nombre, f.motovehiculo.kilometraje, f.id, f.numero, f.estado.label(), f.fechaIngreso));
    }
    List<TallerEstadoResponse> estados = Arrays.stream(FichaState.values()).map(s -> new TallerEstadoResponse(s.label(), buckets.getOrDefault(s, List.of()).stream().limit(100).toList())).toList();
    return new TallerResponse(estados);
  }
  public DashboardFichasResponse dashboardFichas() {
    Map<FichaState, List<DashboardFichaResponse>> buckets = new EnumMap<>(FichaState.class);
    for (Ficha f : db.all("select f from Ficha f where f.deletedAt is null order by f.fechaIngreso desc nulls last, f.createdAt desc", Ficha.class, Map.of())) {
      buckets.computeIfAbsent(f.estado, k -> new ArrayList<>()).add(new DashboardFichaResponse(f.id, f.numero, f.cliente.nombre, f.motovehiculo.marca.nombre + " " + f.motovehiculo.modelo, f.motovehiculo.patente, f.estado.label(), f.total, f.fechaIngreso));
    }
    List<DashboardFichaEstadoResponse> estados = Arrays.stream(FichaState.values()).map(s -> new DashboardFichaEstadoResponse(s.label(), buckets.getOrDefault(s, List.of()).stream().limit(100).toList())).toList();
    return new DashboardFichasResponse(estados);
  }
}
