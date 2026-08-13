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
  private void activoOnly(BaseEntity e) { if (e instanceof Cliente x) x.activo = false; if (e instanceof Motovehiculo x) x.activo = false; if (e instanceof ControlRevision x) x.activo = false; if (e instanceof MarcaMoto x) x.activo = false; if (e instanceof Categoria x) x.activo = false; if (e instanceof AppUser x) x.activo = false; if (e instanceof TrabajoCatalogo x) x.activo = false; }
  private void deleted(BaseEntity e) { activoOnly(e); e.deletedAt = Instant.now(); e.deletedBy = actorId(); }
  private String active(boolean includeDeleted) { return includeDeleted ? "" : " and e.deletedAt is null"; }
  private String dirOf(String dir) { return "DESC".equalsIgnoreCase(dir) ? "DESC" : "ASC"; }
  private String sortable(String requested, Set<String> allowed, String fallback) { return allowed.contains(requested) ? requested : fallback; }
  private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP); }
  private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static String plateKey(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(); }
  private static LocalDate today() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")); }
  private static void touch(Motovehiculo moto) { moto.updatedAt = Instant.now(); }

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
    if (db.count("select count(p) from PropietarioMoto p where p.cliente.id=:id and p.fechaHasta is null and p.deletedAt is null", Map.of("id", id)) > 0) throw new BusinessException(409, "El cliente tiene motos activas");
    deleted(e); audit("Clientes", "ELIMINAR", e.nombre);
  }
  private void copy(ClientRequest r, Cliente e) { e.nombre = r.nombre().trim(); e.documento = blank(r.documento()); e.telefono = r.telefono().trim(); e.email = blank(r.email()); e.direccion = blank(r.direccion()); e.observaciones = blank(r.observaciones()); }
  private ClientResponse client(Cliente e) {
    long motos = db.count("select count(distinct p.motovehiculo.id) from PropietarioMoto p where p.cliente.id=:id and p.fechaHasta is null and p.deletedAt is null", Map.of("id", e.id));
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
    if (clientId != null) { w += " and e.id in (select o.motovehiculo.id from PropietarioMoto o where o.cliente.id=:c and o.fechaHasta is null and o.deletedAt is null)"; ps.put("c", clientId); }
    if (brandId != null) { w += " and e.marca.id=:brand"; ps.put("brand", brandId); }
    if (estado != null && !estado.isBlank()) {
      if (estado.equalsIgnoreCase("En taller") || estado.equalsIgnoreCase("REPARACION")) w += " and e.seccion = com.avianto.back.MotoSection.TALLER and e.ingresada = true";
      else {
        MotoState state = motoStateFilter(estado);
        w += " and e.estadoOperativo=:state"; ps.put("state", state);
      }
    }
    if (activo != null) { w += " and e.activo=:activo"; ps.put("activo", activo); }
    return page("from Motovehiculo e join e.marca", w, "from Motovehiculo e", ps, page, size, sortable(sort, Set.of("modelo", "patente", "kilometraje", "createdAt", "updatedAt"), "patente"), dir, x -> moto((Motovehiculo) x));
  }
  public MotorcycleResponse moto(UUID id) { return moto(db.get(Motovehiculo.class, id)); }
  public PageResponse<ProfileResponse> profiles(String q, String dominio, String motoQuery, String clienteQuery, String estado, int page, int size, String sort, String dir) {
    if (page < 0 || !(size == 10 || size == 20 || size == 50 || size == 100)) throw new BusinessException(400, "Paginación inválida");
    String query = q == null ? "" : q.trim().toLowerCase();
    String domain = plateKey(dominio);
    String vehicle = motoQuery == null ? "" : motoQuery.trim().toLowerCase();
    String client = clienteQuery == null ? "" : clienteQuery.trim().toLowerCase();
    List<ProfileResponse> all = db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true order by e.patente", Motovehiculo.class, Map.of()).stream()
      .map(this::profile)
      .filter(item -> query.isBlank() || plateKey(item.patente()).contains(plateKey(query)) || item.modelo().toLowerCase().contains(query) || (item.propietario() != null && item.propietario().toLowerCase().contains(query)))
      .filter(item -> domain.isBlank() || plateKey(item.patente()).contains(domain))
      .filter(item -> vehicle.isBlank() || (item.marca() + " " + item.modelo()).toLowerCase().contains(vehicle))
      .filter(item -> client.isBlank() || (item.propietario() != null && item.propietario().toLowerCase().contains(client)))
      .filter(item -> estado == null || estado.isBlank() || item.estado().equalsIgnoreCase(estado))
      .toList();
    String selectedSort = sortable(sort, Set.of("patente", "modelo", "estado", "createdAt", "updatedAt"), "patente");
    Comparator<ProfileResponse> comparator = switch (selectedSort) {
      case "modelo" -> Comparator.comparing(ProfileResponse::modelo, String.CASE_INSENSITIVE_ORDER);
      case "estado" -> Comparator.comparing(ProfileResponse::estado, String.CASE_INSENSITIVE_ORDER);
      case "createdAt" -> Comparator.comparing(ProfileResponse::createdAt);
      case "updatedAt" -> Comparator.comparing(ProfileResponse::updatedAt);
      default -> Comparator.comparing(ProfileResponse::patente, String.CASE_INSENSITIVE_ORDER);
    };
    if ("DESC".equalsIgnoreCase(dir)) comparator = comparator.reversed();
    all = all.stream().sorted(comparator).toList();
    int from = Math.min(page * size, all.size());
    int to = Math.min(from + size, all.size());
    return new PageResponse<>(all.subList(from, to), page, size, all.size(), (int) Math.ceil(all.size() / (double) size), selectedSort, dirOf(dir));
  }
  public ProfileResponse profile(UUID id) { return profile(db.get(Motovehiculo.class, id)); }
  public ProfileResponse createProfile(ProfileRequest r) {
    if (r.clienteId() != null && (blank(r.clienteNombre()) != null || blank(r.clienteTelefono()) != null)) throw new BusinessException(400, "Elegí un cliente existente o cargá uno nuevo");
    ClientResponse client;
    if (r.clienteId() != null) {
      Cliente existing = db.get(Cliente.class, r.clienteId());
      if (!existing.activo || existing.deletedAt != null) throw new BusinessException(409, "Cliente inactivo");
      client = client(existing);
    } else {
      if (blank(r.clienteNombre()) == null || blank(r.clienteTelefono()) == null) throw new BusinessException(400, "Ingresá el nombre y teléfono del cliente");
      client = createClient(new ClientRequest(r.clienteNombre(), null, r.clienteTelefono(), null, null, null));
    }
    MotorcycleResponse moto = createMotorcycle(new MotorcycleRequest(client.id(), r.marcaId(), r.modelo(), r.patente(), r.anio(), r.kilometraje(), r.observaciones()));
    audit("Perfiles", "CREAR", moto.patente());
    return profile(db.get(Motovehiculo.class, moto.id()));
  }
  public MotorcycleResponse createMotorcycle(MotorcycleRequest r) {
    Motovehiculo e = new Motovehiculo(); e.estadoOperativo = MotoState.DISPONIBLE; e.ingresada = false; e.seccion = null; copy(r, e); db.persist(e);
    if (r.clienteId() != null) assignInitialOwner(e.id, r.clienteId());
    audit("Motovehículos", "CREAR", e.patente); clearAutocomplete(); return moto(e);
  }
  public MotorcycleResponse updateMotorcycle(UUID id, MotorcycleRequest r) { Motovehiculo e = db.get(Motovehiculo.class, id); copy(r, e); touch(e); audit("Motovehículos", "EDITAR", e.patente); clearAutocomplete(); return moto(e); }
  public MotorcycleResponse ingresarMoto(UUID id, IntakeRequest r) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (!e.activo || e.deletedAt != null) throw new BusinessException(409, "La moto está inactiva");
    if (e.ingresada) throw new BusinessException(409, "La moto ya está ingresada en " + (e.seccion == null ? "el taller" : e.seccion.label()));
    if (e.estadoOperativo == null) throw new BusinessException(409, "La moto tiene un estado inválido");
    if (e.estadoOperativo == MotoState.VENDIDA) throw new BusinessException(409, "La moto vendida es un estado terminal");
    if (e.estadoOperativo != MotoState.DISPONIBLE && e.estadoOperativo != MotoState.ENTREGADA) throw new BusinessException(409, "La moto no está disponible para ingreso");
    MotoSection section = MotoSection.of(r.seccion());
    e.seccion = section;
    e.ingresada = true;
    e.estadoOperativo = section == MotoSection.TALLER ? MotoState.INGRESADA_TALLER : MotoState.EN_VENTA;
    audit("Motovehículos", "INGRESAR", e.patente + " -> " + section.label());
    return moto(e);
  }
  public MotorcycleResponse completarVenta(UUID id) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (e.seccion != MotoSection.VENTA || e.estadoOperativo != MotoState.TRANSFERENCIA_EN_PROCESO) throw new BusinessException(409, "La moto no tiene una transferencia en proceso");
    e.ingresada = false;
    e.estadoOperativo = MotoState.VENDIDA;
    audit("Ventas", "VENDER", e.patente);
    return moto(e);
  }
  public void deleteMotorcycle(UUID id) {
    Motovehiculo e = db.get(Motovehiculo.class, id);
    if (db.count("select count(f) from Ficha f where f.motovehiculo.id=:id and f.deletedAt is null", Map.of("id", id)) > 0) throw new BusinessException(409, "La moto tiene fichas activas");
    deleted(e); touch(e); audit("Motovehículos", "ELIMINAR", e.patente); clearAutocomplete();
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
  private void requireTallerIngresada(Motovehiculo moto) {
    if (moto.seccion != MotoSection.TALLER || !moto.ingresada || !EnumSet.of(MotoState.INGRESADA_TALLER, MotoState.PENDIENTE, MotoState.EN_PROCESO, MotoState.REVISION, MotoState.TERMINADA).contains(moto.estadoOperativo)) throw new BusinessException(409, "La moto debe estar ingresada en Taller");
  }

  private MotorcycleResponse moto(Motovehiculo e) {
    PropietarioMoto o = propietarioActual(e.id);
    return new MotorcycleResponse(e.id, o == null ? null : o.cliente.id, o == null ? null : o.cliente.nombre, e.marca.id, e.marca.nombre, e.modelo, e.patente, e.anio, e.kilometraje, e.seccion == null ? null : e.seccion.label(), e.ingresada, estadoMoto(e), e.kmUltimoService, e.fechaUltimoService, e.kmServicePeriodo, e.mesesServicePeriodo, e.serviceObservaciones, e.observaciones, e.activo, e.createdAt, e.updatedAt);
  }
  private ProfileResponse profile(Motovehiculo e) {
    PropietarioMoto o = propietarioActual(e.id);
    return new ProfileResponse(e.id, o == null ? null : o.cliente.id, o == null ? null : o.cliente.nombre, e.marca.id, e.marca.nombre, e.modelo, e.patente, e.anio, e.kilometraje, e.seccion == null ? null : e.seccion.label(), e.ingresada, estadoMoto(e), e.kmUltimoService, e.fechaUltimoService, e.kmServicePeriodo, e.mesesServicePeriodo, e.serviceObservaciones, e.observaciones, e.activo, e.createdAt, e.updatedAt);
  }
  String estadoMoto(UUID motoId) { return estadoMoto(db.get(Motovehiculo.class, motoId)); }
  private String estadoMoto(Motovehiculo moto) { return moto.estadoOperativo == null ? MotoState.DISPONIBLE.label() : moto.estadoOperativo.label(); }
  private MotoState motoStateFilter(String value) {
    try { return MotoState.of(value); }
    catch (BusinessException ignored) {
      return switch (FichaState.of(value)) {
        case PENDIENTE -> MotoState.PENDIENTE;
        case EN_PROCESO -> MotoState.EN_PROCESO;
        case REVISION -> MotoState.REVISION;
        case TERMINADA -> MotoState.TERMINADA;
        case ENTREGADA, CANCELADA -> MotoState.ENTREGADA;
      };
    }
  }
  private PropietarioMoto propietarioActual(UUID motoId) { return db.one("select p from PropietarioMoto p where p.motovehiculo.id=:moto and p.fechaHasta is null and p.deletedAt is null", PropietarioMoto.class, Map.of("moto", motoId)); }
  @Cacheable(value = "autocomplete", key = "'motorcycles:' + #q") public List<AutocompleteResponse> motorcycleAutocomplete(String q) {
    return db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true and (lower(e.modelo) like lower(:q) or lower(e.patente) like lower(:q)) order by e.patente", Motovehiculo.class, Map.of("q", "%" + q.toLowerCase() + "%")).stream().limit(15).map(e -> new AutocompleteResponse(e.id, e.patente, e.marca.nombre + " " + e.modelo)).toList();
  }

  // ---------- Propietarios (toda la historia; actual = fechaHasta null) ----------
  public List<OwnerResponse> owners(UUID motoId) {
    return db.all("select e from PropietarioMoto e join e.cliente where e.motovehiculo.id=:moto and e.deletedAt is null order by e.fechaDesde desc", PropietarioMoto.class, Map.of("moto", motoId)).stream().map(this::owner).toList();
  }
  private void assignInitialOwner(UUID motoId, UUID clientId) {
    Motovehiculo m = db.get(Motovehiculo.class, motoId);
    Cliente c = db.get(Cliente.class, clientId);
    if (!c.activo || c.deletedAt != null) throw new BusinessException(409, "Cliente inactivo");
    PropietarioMoto actual = propietarioActual(motoId);
    if (actual != null) throw new BusinessException(409, "La moto ya tiene un propietario actual");
    PropietarioMoto n = new PropietarioMoto(); n.motovehiculo = m; n.cliente = c; n.fechaDesde = today();
    db.persist(n);
  }
  public PageResponse<TransferResponse> transfers(String q, LocalDate desde, LocalDate hasta, int page, int size, String sort, String dir) {
    Map<String,Object> ps = p();
    String where = " where e.deletedAt is null";
    if (q != null && !q.isBlank()) { where += " and (lower(e.motovehiculo.patente) like lower(:q) or lower(e.clienteAnterior.nombre) like lower(:q) or lower(e.clienteNuevo.nombre) like lower(:q))"; ps.put("q", "%" + q.trim() + "%"); }
    if (desde != null) { where += " and e.fechaTransferencia>=:desde"; ps.put("desde", desde); }
    if (hasta != null) { where += " and e.fechaTransferencia<=:hasta"; ps.put("hasta", hasta); }
    return page("from TransferenciaMoto e join e.motovehiculo join e.clienteAnterior join e.clienteNuevo", where, "from TransferenciaMoto e", ps, page, size, sortable(sort, Set.of("fechaTransferencia", "createdAt"), "fechaTransferencia"), dir, x -> transfer((TransferenciaMoto) x));
  }
  public List<TransferResponse> transfersForMotorcycle(UUID motoId) {
    db.get(Motovehiculo.class, motoId);
    return db.all("select e from TransferenciaMoto e join e.motovehiculo join e.clienteAnterior join e.clienteNuevo where e.motovehiculo.id=:moto and e.deletedAt is null order by e.fechaTransferencia desc, e.createdAt desc", TransferenciaMoto.class, Map.of("moto", motoId)).stream().map(this::transfer).toList();
  }
  public TransferResponse createTransfer(TransferRequest r) {
    Motovehiculo m = db.get(Motovehiculo.class, r.motoId());
    if (!m.activo || m.deletedAt != null) throw new BusinessException(409, "La moto está inactiva");
    if (m.seccion != MotoSection.VENTA || !m.ingresada || m.estadoOperativo != MotoState.EN_VENTA) throw new BusinessException(409, "La moto debe estar ingresada en Ventas y marcada En venta");
    Cliente nuevo = db.get(Cliente.class, r.clienteNuevoId());
    if (!nuevo.activo || nuevo.deletedAt != null) throw new BusinessException(409, "Cliente inactivo");
    PropietarioMoto actual = propietarioActual(m.id);
    if (actual == null) throw new BusinessException(409, "La moto no tiene un propietario actual");
    if (actual.cliente.id.equals(nuevo.id)) throw new BusinessException(409, "El cliente ya es el propietario actual");
    LocalDate fecha = r.fechaTransferencia();
    if (fecha.isAfter(today())) throw new BusinessException(422, "La fecha no puede ser futura");
    if (actual.fechaDesde != null && !fecha.isAfter(actual.fechaDesde)) throw new BusinessException(422, "La fecha debe ser posterior al inicio del período actual");
    actual.fechaHasta = fecha.minusDays(1);
    db.flush();
    PropietarioMoto siguiente = new PropietarioMoto(); siguiente.motovehiculo = m; siguiente.cliente = nuevo; siguiente.fechaDesde = fecha; siguiente.observaciones = blank(r.observaciones());
    db.persist(siguiente);
    TransferenciaMoto e = new TransferenciaMoto(); e.motovehiculo = m; e.clienteAnterior = actual.cliente; e.clienteNuevo = nuevo; e.fechaTransferencia = fecha; e.observaciones = blank(r.observaciones()); e.realizadaPor = actor();
    db.persist(e);
    m.estadoOperativo = MotoState.TRANSFERENCIA_EN_PROCESO;
    audit("Transferencias", "TRANSFERIR", m.patente + " · " + actual.cliente.nombre + " -> " + nuevo.nombre);
    clearAutocomplete();
    return transfer(e);
  }
  public TransferResponse updateTransfer(UUID id, TransferUpdateRequest r) {
    TransferenciaMoto e = db.get(TransferenciaMoto.class, id);
    if (e.deletedAt != null) throw new NotFoundException("TransferenciaMoto inexistente");
    Cliente nuevo = db.get(Cliente.class, r.clienteNuevoId());
    if (!nuevo.activo || nuevo.deletedAt != null) throw new BusinessException(409, "Cliente inactivo");
    if (r.fechaTransferencia().isAfter(today())) throw new BusinessException(422, "La fecha no puede ser futura");
    e.clienteNuevo = nuevo;
    e.fechaTransferencia = r.fechaTransferencia();
    e.observaciones = blank(r.observaciones());
    rebuildOwnership(e.motovehiculo.id);
    audit("Transferencias", "EDITAR", e.motovehiculo.patente + " · " + e.clienteNuevo.nombre);
    clearAutocomplete();
    return transfer(e);
  }
  public void deleteTransfer(UUID id) {
    TransferenciaMoto e = db.get(TransferenciaMoto.class, id);
    if (e.deletedAt != null) throw new NotFoundException("TransferenciaMoto inexistente");
    String description = e.motovehiculo.patente + " · " + e.clienteAnterior.nombre + " -> " + e.clienteNuevo.nombre;
    UUID motoId = e.motovehiculo.id;
    deleted(e);
    if (e.motovehiculo.seccion == MotoSection.VENTA && e.motovehiculo.estadoOperativo == MotoState.TRANSFERENCIA_EN_PROCESO) e.motovehiculo.estadoOperativo = MotoState.EN_VENTA;
    rebuildOwnership(motoId);
    audit("Transferencias", "ELIMINAR", description);
    clearAutocomplete();
  }
  private void rebuildOwnership(UUID motoId) {
    List<PropietarioMoto> owners = db.all("select e from PropietarioMoto e join e.cliente where e.motovehiculo.id=:moto and e.deletedAt is null order by e.fechaDesde asc, e.createdAt asc", PropietarioMoto.class, Map.of("moto", motoId));
    if (owners.isEmpty()) throw new BusinessException(409, "La moto no tiene historial de propietarios");
    PropietarioMoto initial = owners.get(0);
    if (initial.fechaDesde == null) throw new BusinessException(409, "El historial de propietarios no tiene fecha de inicio");
    List<TransferenciaMoto> transfers = db.all("select e from TransferenciaMoto e join e.clienteAnterior join e.clienteNuevo where e.motovehiculo.id=:moto and e.deletedAt is null order by e.fechaTransferencia asc, e.createdAt asc", TransferenciaMoto.class, Map.of("moto", motoId));
    Cliente previous = initial.cliente;
    LocalDate previousDate = initial.fechaDesde;
    for (TransferenciaMoto event : transfers) {
      if (!event.fechaTransferencia.isAfter(previousDate)) throw new BusinessException(422, "Las fechas de transferencia deben ser posteriores y únicas");
      if (event.clienteNuevo.id.equals(previous.id)) throw new BusinessException(409, "El cliente ya es el propietario anterior");
      previous = event.clienteNuevo;
      previousDate = event.fechaTransferencia;
    }
    for (PropietarioMoto owner : owners.subList(1, owners.size())) deleted(owner);
    initial.fechaHasta = transfers.isEmpty() ? null : transfers.get(0).fechaTransferencia.minusDays(1);
    db.flush();
    previous = initial.cliente;
    for (int i = 0; i < transfers.size(); i++) {
      TransferenciaMoto event = transfers.get(i);
      event.clienteAnterior = previous;
      PropietarioMoto owner = new PropietarioMoto();
      owner.motovehiculo = event.motovehiculo;
      owner.cliente = event.clienteNuevo;
      owner.fechaDesde = event.fechaTransferencia;
      owner.fechaHasta = i + 1 < transfers.size() ? transfers.get(i + 1).fechaTransferencia.minusDays(1) : null;
      owner.observaciones = event.observaciones;
      db.persist(owner);
      previous = event.clienteNuevo;
    }
  }
  private OwnerResponse owner(PropietarioMoto e) { return new OwnerResponse(e.id, e.cliente.id, e.cliente.nombre, e.fechaDesde, e.fechaHasta, e.fechaHasta == null, e.observaciones); }
  private TransferResponse transfer(TransferenciaMoto e) { return new TransferResponse(e.id, e.motovehiculo.id, e.motovehiculo.patente, e.motovehiculo.marca.nombre + " " + e.motovehiculo.modelo, e.clienteAnterior.id, e.clienteAnterior.nombre, e.clienteNuevo.id, e.clienteNuevo.nombre, e.fechaTransferencia, e.observaciones, e.realizadaPor == null ? null : e.realizadaPor.nombre, e.createdAt); }

  // ---------- Service ----------
  public List<ServiceResponse> services(UUID motoId) {
    return db.all("select e from ServiceMoto e join e.motovehiculo left join e.ficha where e.motovehiculo.id=:moto order by e.fecha desc", ServiceMoto.class, Map.of("moto", motoId)).stream().map(this::service).toList();
  }
  public PageResponse<ServiceResponse> serviceHistory(UUID motoId, LocalDate desde, LocalDate hasta, int page, int size, String sort, String dir) {
    db.get(Motovehiculo.class, motoId);
    Map<String,Object> ps = p(); ps.put("moto", motoId);
    String w = " where e.motovehiculo.id=:moto";
    if (desde != null) { w += " and e.fecha>=:desde"; ps.put("desde", desde); }
    if (hasta != null) { w += " and e.fecha<=:hasta"; ps.put("hasta", hasta); }
    return page("from ServiceMoto e join e.motovehiculo left join e.ficha", w, "from ServiceMoto e", ps, page, size, sortable(sort, Set.of("fecha", "kilometraje", "createdAt"), "fecha"), dir, x -> service((ServiceMoto) x));
  }
  public ServiceResponse addService(UUID motoId, ServiceRequest req) {
    if (req.kilometraje() == null || req.kilometraje() < 0) throw new BusinessException(400, "Kilometraje inválido");
    Motovehiculo m = db.get(Motovehiculo.class, motoId);
    LocalDate fecha = req.fecha() == null ? today() : req.fecha();
    if (fecha.isAfter(today())) throw new BusinessException(422, "La fecha del service no puede ser futura");
    Ficha ficha = req.fichaId() == null ? null : db.get(Ficha.class, req.fichaId());
    assertValidFichaLink(ficha, m, "El service");
    ServiceMoto e = new ServiceMoto();
    e.motovehiculo = m; e.ficha = ficha;
    e.kilometraje = req.kilometraje(); e.fecha = fecha; e.observaciones = blank(req.observaciones()); e.realizadoPor = actor();
    db.persist(e);
    if (m.fechaUltimoService == null || !e.fecha.isBefore(m.fechaUltimoService)) {
      if (m.kmUltimoService != null && e.kilometraje < m.kmUltimoService) throw new BusinessException(409, "El kilometraje no puede ser menor al último service de esa fecha");
      m.kmUltimoService = e.kilometraje; m.fechaUltimoService = e.fecha;
    }
    if (m.kilometraje == null || e.kilometraje > m.kilometraje) m.kilometraje = e.kilometraje;
    audit("Services", "REGISTRAR", m.patente + " km " + e.kilometraje);
    return service(e);
  }
  private ServiceResponse service(ServiceMoto e) { return new ServiceResponse(e.id, e.motovehiculo.id, e.ficha == null ? null : e.ficha.id, e.ficha == null ? null : e.ficha.numero, e.kilometraje, e.fecha, e.observaciones, e.realizadoPor == null ? null : e.realizadoPor.nombre, e.createdAt); }
  private void assertValidFichaLink(Ficha ficha, Motovehiculo moto, String entity) {
    if (ficha != null && (ficha.deletedAt != null || ficha.estado == FichaState.CANCELADA || !ficha.motovehiculo.id.equals(moto.id) || ficha.trabajos.stream().noneMatch(t -> t.estadoTrabajo != TrabajoState.CANCELADO))) throw new BusinessException(422, entity + " no puede asociarse a una ficha inválida");
  }
  public List<NextServiceResponse> nextServices() {
    LocalDate h = today();
    return db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true and e.estadoOperativo <> com.avianto.back.MotoState.VENDIDA order by e.patente", Motovehiculo.class, Map.of()).stream().map(m -> {
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
    return page("from Ficha e join e.cliente join e.motovehiculo", w, "from Ficha e", ps, page, size, sortable(sort, Set.of("createdAt", "fechaIngreso", "estado", "estadoPago", "total", "numero"), "fechaIngreso"), dir, x -> ficha((Ficha) x));
  }
  public FichaResponse ficha(UUID id) { return ficha(db.get(Ficha.class, id)); }
  public FichaResponse createFicha(FichaRequest r) {
    Ficha e = new Ficha(); copy(r, e);
    requireTallerIngresada(e.motovehiculo);
    requireAtLeastOneTrabajo(e);
    assertNoOpenFicha(e.motovehiculo.id, null);
    e.numero = "F-" + db.nextVal("ficha_numero_seq");
    db.persist(e);
    e.motovehiculo.estadoOperativo = MotoState.PENDIENTE;
    audit("Fichas", "CREAR", e.numero);
    return ficha(e);
  }
  public FichaResponse updateFicha(UUID id, FichaRequest r) { Ficha e = db.get(Ficha.class, id); assertEditable(e); copy(r, e); requireAtLeastOneTrabajo(e); recalcFichaPayment(e); requireTallerIngresada(e.motovehiculo); assertNoOpenFicha(e.motovehiculo.id, e.id); if (e.estado == FichaState.PENDIENTE) e.motovehiculo.estadoOperativo = MotoState.PENDIENTE; audit("Fichas", "EDITAR", e.numero); return ficha(e); }
  public void deleteFicha(UUID id) { Ficha e = db.get(Ficha.class, id); if (e.estado == FichaState.TERMINADA || e.estado == FichaState.ENTREGADA) throw new BusinessException(409, "No puede eliminarse una ficha finalizada"); deleted(e); audit("Fichas", "ELIMINAR", e.numero); }
  public FichaResponse fichaState(UUID id, StateRequest r) {
    Ficha e = db.get(Ficha.class, id); FichaState next = FichaState.of(r.estado());
    if (next == FichaState.CANCELADA && e.estado != FichaState.TERMINADA && e.estado != FichaState.ENTREGADA && e.estado != FichaState.CANCELADA) { e.estado = next; syncMotoAfterFichaCancellation(e); }
    else if (e.estado == FichaState.PENDIENTE && next == FichaState.EN_PROCESO && !e.trabajos.isEmpty()) { e.estado = next; e.motovehiculo.estadoOperativo = MotoState.EN_PROCESO; }
    else if (e.estado == FichaState.EN_PROCESO && next == FichaState.REVISION && trabajosFinalizados(e)) { e.estado = next; e.motovehiculo.estadoOperativo = MotoState.REVISION; }
    else if (e.estado == FichaState.REVISION && next == FichaState.EN_PROCESO) { e.estado = next; e.motovehiculo.estadoOperativo = MotoState.EN_PROCESO; }
    else if (e.estado == FichaState.EN_PROCESO && next == FichaState.PENDIENTE) { e.estado = next; e.motovehiculo.estadoOperativo = MotoState.PENDIENTE; }
    else throw new BusinessException(422, "Transición de ficha inválida");
    audit("Fichas", "ESTADO", e.numero + " -> " + e.estado.label()); return ficha(e);
  }
  public FichaResponse entregarFicha(UUID id) {
    Ficha e = db.get(Ficha.class, id);
    if (e.estado != FichaState.TERMINADA) throw new BusinessException(409, "La ficha debe estar terminada para entregar la moto");
    e.estado = FichaState.ENTREGADA;
    if (e.fechaEntregaReal == null) e.fechaEntregaReal = today();
    e.motovehiculo.ingresada = false;
    e.motovehiculo.estadoOperativo = MotoState.ENTREGADA;
    audit("Fichas", "ENTREGAR", e.numero);
    return ficha(e);
  }
  public FichaResponse fichaPago(UUID id, PagoRequest r) {
    Ficha e = db.get(Ficha.class, id);
    if (e.estado == FichaState.CANCELADA) throw new BusinessException(409, "No puede registrarse un pago en una ficha cancelada");
    applyFichaPayment(e, PagoState.of(r.estadoPago()), r.itemIds());
    audit("Fichas", "PAGO", e.numero + " -> " + e.estadoPago.label()); return ficha(e);
  }
  private void assertEditable(Ficha e) { if (e.estado == FichaState.TERMINADA || e.estado == FichaState.ENTREGADA || e.estado == FichaState.CANCELADA) throw new BusinessException(409, "La ficha ya finalizó"); }
  private void copy(FichaRequest r, Ficha e) {
    Cliente c = db.get(Cliente.class, r.clienteId());
    Motovehiculo m = db.get(Motovehiculo.class, r.motoId());
    if (!c.activo || !m.activo || c.deletedAt != null || m.deletedAt != null) throw new BusinessException(409, "Cliente o moto inactivo");
    assertCurrentOwner(c, m);
    e.cliente = c; e.motovehiculo = m;
    e.fechaIngreso = r.fechaIngreso() == null ? today() : r.fechaIngreso();
    e.fechaEntregaEstimada = r.fechaEntregaEstimada();
    e.kilometrajeIngreso = r.kilometrajeIngreso();
    e.vencimiento = r.vencimiento();
    e.observaciones = blank(r.observaciones());
    e.descuentoGlobal = money(r.descuentoGlobal());
    e.iva = r.iva();
    replaceTrabajos(e, r.trabajos());
    recalc(e);
  }
  private void replaceTrabajos(Ficha e, List<FichaTrabajoRequest> requests) {
    if (requests == null) throw new BusinessException(400, "La ficha debe tener al menos un trabajo");
    List<FichaTrabajo> old = new ArrayList<>(e.trabajos);
    Set<UUID> retained = new HashSet<>();
    for (FichaTrabajoRequest request : requests) {
      FichaTrabajo t = request.id() == null ? old.stream().filter(x -> !retained.contains(x.id) && x.estadoTrabajo != TrabajoState.CANCELADO && x.descripcion.equalsIgnoreCase(request.descripcion().trim())).findFirst().orElse(null) : old.stream().filter(x -> x.id.equals(request.id())).findFirst().orElse(null);
      if (request.id() != null && t == null) throw new NotFoundException("El trabajo no existe");
      if (t == null) { t = new FichaTrabajo(); t.ficha = e; e.trabajos.add(t); }
      copyTrabajo(t, request, t.id != null && old.contains(t));
      retained.add(t.id);
    }
    old.stream().filter(x -> !retained.contains(x.id) && x.estadoTrabajo != TrabajoState.CANCELADO).forEach(x -> { x.estadoTrabajo = TrabajoState.CANCELADO; x.pagado = false; });
  }
  private void applyTrabajo(Ficha e, FichaTrabajoRequest r, UUID id) {
    FichaTrabajo t = new FichaTrabajo();
    if (id != null) t.id = id;
    t.ficha = e;
    copyTrabajo(t, r, false);
    e.trabajos.add(t);
  }
  private void copyTrabajo(FichaTrabajo t, FichaTrabajoRequest r, boolean existing) {
    t.descripcion = r.descripcion().trim();
    t.precioAplicado = money(r.precioUnitario());
    t.descuento = money(r.descuento());
    t.subtotal = money(t.precioAplicado.subtract(t.descuento));
    if (t.subtotal.signum() < 0) throw new BusinessException(400, "Descuento de línea inválido");
    TrabajoState next = (r.estadoTrabajo() == null || r.estadoTrabajo().isBlank()) ? (existing ? t.estadoTrabajo : TrabajoState.PENDIENTE) : TrabajoState.of(r.estadoTrabajo());
    if (existing && next != t.estadoTrabajo && !validTrabajoTransition(t.estadoTrabajo, next)) throw new BusinessException(422, "Transición de trabajo inválida");
    t.estadoTrabajo = next;
    t.observacionTrabajo = blank(r.observacionTrabajo());
    if (t.estadoTrabajo == TrabajoState.REALIZADO) { if (t.completadoAt == null) t.completadoAt = Instant.now(); if (t.completadoPor == null) t.completadoPor = actorId(); }
    else { t.completadoAt = null; t.completadoPor = null; if (t.estadoTrabajo == TrabajoState.CANCELADO) t.pagado = false; }
  }
  private void recalc(Ficha e) {
    BigDecimal sum = e.trabajos.stream().filter(x -> x.estadoTrabajo != TrabajoState.CANCELADO).map(x -> x.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    sum = sum.subtract(e.descuentoGlobal);
    if (sum.signum() < 0) throw new BusinessException(400, "Descuento global inválido");
    e.total = money(e.iva ? sum.multiply(new BigDecimal("1.21")) : sum);
  }
  private void assertCurrentOwner(Cliente c, Motovehiculo m) {
    PropietarioMoto owner = propietarioActual(m.id);
    if (owner == null || !owner.cliente.id.equals(c.id)) throw new BusinessException(422, "El cliente no es el propietario actual de la moto");
  }
  private void assertNoOpenFicha(UUID motoId, UUID excludedId) {
    Map<String,Object> ps = p(); ps.put("moto", motoId);
    String excluded = "";
    if (excludedId != null) { excluded = " and f.id <> :id"; ps.put("id", excludedId); }
    if (db.count("select count(f) from Ficha f where f.motovehiculo.id=:moto and f.deletedAt is null and f.estado not in (com.avianto.back.FichaState.ENTREGADA, com.avianto.back.FichaState.CANCELADA)" + excluded, ps) > 0) throw new BusinessException(409, "La moto ya tiene una ficha abierta");
  }
  private void requireAtLeastOneTrabajo(Ficha ficha) { if (ficha.trabajos.stream().noneMatch(t -> t.estadoTrabajo != TrabajoState.CANCELADO)) throw new BusinessException(400, "La ficha debe tener al menos un trabajo"); }
  private boolean validTrabajoTransition(TrabajoState current, TrabajoState next) { return (current == TrabajoState.PENDIENTE && (next == TrabajoState.REALIZADO || next == TrabajoState.CANCELADO)) || (current == TrabajoState.REALIZADO && (next == TrabajoState.PENDIENTE || next == TrabajoState.CANCELADO)); }
  private void syncMotoAfterFichaCancellation(Ficha ficha) { ficha.motovehiculo.ingresada = false; ficha.motovehiculo.estadoOperativo = MotoState.ENTREGADA; touch(ficha.motovehiculo); }
  private boolean trabajosFinalizados(Ficha e) { return !e.trabajos.isEmpty() && e.trabajos.stream().allMatch(x -> x.estadoTrabajo == TrabajoState.REALIZADO || x.estadoTrabajo == TrabajoState.CANCELADO); }
  private FichaResponse ficha(Ficha e) {
    List<FichaTrabajoResponse> lines = e.trabajos.stream().map(t -> new FichaTrabajoResponse(t.id, t.descripcion, t.precioAplicado, t.descuento, t.subtotal, t.estadoTrabajo.label(), t.observacionTrabajo, t.completadoAt, t.completadoPor, t.pagado)).toList();
    List<PhotoResponse> fotos = e.fotos.stream().map(f -> photo(e.id, f)).toList();
    return new FichaResponse(e.id, e.numero, e.cliente.id, e.motovehiculo.id, e.cliente.nombre, e.motovehiculo.marca.nombre + " " + e.motovehiculo.modelo, e.motovehiculo.patente, e.vencimiento, e.fechaIngreso, e.fechaEntregaEstimada, e.fechaEntregaReal, e.kilometrajeIngreso, e.observaciones, e.descuentoGlobal, e.iva, e.estado.label(), e.estadoPago.label(), e.total, e.createdAt, lines, fotos);
  }
  public FichaResponse addTrabajo(UUID id, FichaTrabajoRequest r) { Ficha e = db.get(Ficha.class, id); assertEditable(e); applyTrabajo(e, r, null); recalc(e); recalcFichaPayment(e); audit("Fichas", "TRABAJO", e.numero); return ficha(e); }
  public FichaResponse updateTrabajo(UUID id, UUID trabajoId, FichaTrabajoRequest r) {
    Ficha e = db.get(Ficha.class, id); assertEditable(e);
    FichaTrabajo old = e.trabajos.stream().filter(x -> x.id.equals(trabajoId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    copyTrabajo(old, r, true); recalc(e); recalcFichaPayment(e);
    audit("Fichas", "TRABAJO", e.numero); return ficha(e);
  }
  public void deleteTrabajo(UUID id, UUID trabajoId) {
    Ficha e = db.get(Ficha.class, id); assertEditable(e);
    FichaTrabajo target = e.trabajos.stream().filter(x -> x.id.equals(trabajoId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    if (e.trabajos.stream().filter(x -> x.estadoTrabajo != TrabajoState.CANCELADO).count() <= 1) throw new BusinessException(409, "La ficha debe conservar al menos un trabajo");
    target.estadoTrabajo = TrabajoState.CANCELADO; target.pagado = false; recalc(e); recalcFichaPayment(e); audit("Fichas", "QUITAR TRABAJO", e.numero);
  }
  public FichaResponse trabajoState(UUID id, UUID trabajoId, StateRequest r) {
    Ficha e = db.get(Ficha.class, id);
    assertEditable(e);
    FichaTrabajo target = e.trabajos.stream().filter(x -> x.id.equals(trabajoId)).findFirst().orElseThrow(() -> new NotFoundException("El trabajo no existe"));
    TrabajoState next = TrabajoState.of(r.estado());
    if (target.estadoTrabajo == TrabajoState.CANCELADO || !validTrabajoTransition(target.estadoTrabajo, next)) throw new BusinessException(422, "Transición de trabajo inválida");
    target.estadoTrabajo = next;
    if (next == TrabajoState.REALIZADO) { target.completadoAt = Instant.now(); target.completadoPor = actorId(); }
    else { target.completadoAt = null; target.completadoPor = null; target.pagado = false; }
    if (next == TrabajoState.PENDIENTE && e.estado == FichaState.REVISION) { e.estado = FichaState.EN_PROCESO; e.motovehiculo.estadoOperativo = MotoState.EN_PROCESO; }
    audit("Fichas", "TRABAJO ESTADO", e.numero + " " + target.descripcion + " -> " + next.label());
    if ((next == TrabajoState.REALIZADO || next == TrabajoState.CANCELADO) && e.estado == FichaState.EN_PROCESO && trabajosFinalizados(e)) { e.estado = FichaState.REVISION; e.motovehiculo.estadoOperativo = MotoState.REVISION; }
    recalcFichaPayment(e);
    return ficha(e);
  }

  private void applyFichaPayment(Ficha ficha, PagoState requested, List<UUID> selectedIds) {
    List<FichaTrabajo> eligible = ficha.trabajos.stream().filter(t -> t.estadoTrabajo == TrabajoState.REALIZADO).toList();
    if (requested == PagoState.NO_PAGADO) eligible.forEach(t -> t.pagado = false);
    else if (requested == PagoState.PAGADO) eligible.forEach(t -> t.pagado = true);
    else {
      if (eligible.isEmpty()) throw new BusinessException(422, "La ficha no tiene trabajos realizados para registrar un pago parcial");
      Set<UUID> ids = selectedIds == null ? Set.of() : new HashSet<>(selectedIds);
      Set<UUID> eligibleIds = eligible.stream().map(t -> t.id).collect(Collectors.toSet());
      if (ids.isEmpty() || !eligibleIds.containsAll(ids)) throw new BusinessException(422, "Seleccioná al menos un trabajo realizado válido");
      eligible.forEach(t -> t.pagado = ids.contains(t.id));
    }
    recalcFichaPayment(ficha);
  }

  private void recalcFichaPayment(Ficha ficha) {
    List<FichaTrabajo> eligible = ficha.trabajos.stream().filter(t -> t.estadoTrabajo == TrabajoState.REALIZADO).toList();
    long paid = eligible.stream().filter(t -> t.pagado).count();
    ficha.estadoPago = paid == 0 ? PagoState.NO_PAGADO : paid == eligible.size() ? PagoState.PAGADO : PagoState.PARCIAL;
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
    return page("from RepuestoPedido e join e.motovehiculo join e.cliente", w, "from RepuestoPedido e", ps, page, size, sortable(sort, Set.of("fecha", "createdAt", "total", "estado", "estadoPago"), "fecha"), dir, x -> repuesto((RepuestoPedido) x));
  }
  public RepuestoResponse repuesto(UUID id) { return repuesto(db.get(RepuestoPedido.class, id)); }
  public RepuestoResponse createRepuesto(RepuestoRequest r) {
    if (r.items() == null || r.items().isEmpty()) throw new BusinessException(400, "El pedido debe tener al menos un ítem");
    Motovehiculo m = db.get(Motovehiculo.class, r.motoVehiculoId());
    Cliente c = db.get(Cliente.class, r.clienteId());
    RepuestoPedido e = new RepuestoPedido();
    applyRepuestoLinks(e, m, c, r.fichaId());
    e.numero = "R-" + db.nextVal("repuesto_pedido_numero_seq");
    e.fecha = r.fecha() == null ? today() : r.fecha();
    if (e.fecha.isAfter(today())) throw new BusinessException(422, "La fecha del pedido no puede ser futura");
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
    applyRepuestoLinks(e, db.get(Motovehiculo.class, r.motoVehiculoId()), db.get(Cliente.class, r.clienteId()), r.fichaId());
    e.fecha = r.fecha() == null ? today() : r.fecha();
    if (e.fecha.isAfter(today())) throw new BusinessException(422, "La fecha del pedido no puede ser futura");
    e.proveedor = blank(r.proveedor()); e.observaciones = blank(r.observaciones());
    replaceRepuestoItems(e, r.items());
    recalcRepuesto(e); recalcRepuestoPayment(e);
    audit("REPUESTOS", "EDITAR", e.numero);
    return repuesto(e);
  }
  public void deleteRepuesto(UUID id) { RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e); deleted(e); audit("REPUESTOS", "ELIMINAR", e.numero); }
  private void assertRepuestoEditable(RepuestoPedido e) { if (e.estado == RepuestoPedidoState.COMPLETADO || e.estado == RepuestoPedidoState.CANCELADO) throw new BusinessException(409, "El pedido ya finalizó"); }
  private void applyRepuestoLinks(RepuestoPedido e, Motovehiculo m, Cliente c, UUID fichaId) {
    if (!m.activo || !c.activo || m.deletedAt != null || c.deletedAt != null) throw new BusinessException(409, "Cliente o moto inactivo");
    requireTallerIngresada(m);
    assertCurrentOwner(c, m);
    Ficha ficha = fichaId == null ? null : db.get(Ficha.class, fichaId);
    if (ficha != null && (!ficha.motovehiculo.id.equals(m.id) || !ficha.cliente.id.equals(c.id))) throw new BusinessException(422, "La ficha no corresponde al cliente y la moto del pedido");
    assertValidFichaLink(ficha, m, "El pedido");
    e.motovehiculo = m; e.cliente = c; e.ficha = ficha;
  }
  private void applyRepuestoItem(RepuestoPedido e, RepuestoItemRequest r, UUID id) {
    RepuestoPedidoItem i = new RepuestoPedidoItem();
    if (id != null) i.id = id;
    i.pedido = e;
    if (r.fichaTrabajoId() != null) {
      if (e.ficha == null) throw new BusinessException(422, "Un ítem con trabajo requiere una ficha asociada");
      i.fichaTrabajo = db.get(FichaTrabajo.class, r.fichaTrabajoId());
      if (!i.fichaTrabajo.ficha.id.equals(e.ficha.id) || i.fichaTrabajo.estadoTrabajo == TrabajoState.CANCELADO) throw new BusinessException(422, "El trabajo no pertenece a una ficha válida del pedido");
    }
    i.descripcion = r.descripcion().trim(); i.tipo = r.tipo(); i.cantidad = r.cantidad() == null ? BigDecimal.ONE : r.cantidad(); i.precio = money(r.precio());
    i.subtotal = money(i.cantidad.multiply(i.precio));
    i.estado = r.estado() == null || r.estado().isBlank() ? RepuestoItemState.PENDIENTE_DE_PEDIR : RepuestoItemState.of(r.estado());
    i.observaciones = blank(r.observaciones());
    e.items.add(i);
  }
  private void replaceRepuestoItems(RepuestoPedido e, List<RepuestoItemRequest> requests) {
    List<RepuestoPedidoItem> old = new ArrayList<>(e.items);
    Set<UUID> retained = new HashSet<>();
    for (RepuestoItemRequest request : requests) {
      RepuestoPedidoItem item = old.stream().filter(x -> !retained.contains(x.id) && (request.id() != null ? x.id.equals(request.id()) : Objects.equals(request.fichaTrabajoId(), x.fichaTrabajo == null ? null : x.fichaTrabajo.id) && x.descripcion.equalsIgnoreCase(request.descripcion().trim()))).findFirst().orElse(null);
      if (request.id() != null && item == null) throw new NotFoundException("El ítem no existe");
      if (item == null) { applyRepuestoItem(e, request, null); item = e.items.get(e.items.size() - 1); }
      else copyRepuestoItem(item, request);
      retained.add(item.id);
    }
    old.stream().filter(x -> !retained.contains(x.id) && x.estado != RepuestoItemState.CANCELADO).forEach(x -> { x.estado = RepuestoItemState.CANCELADO; x.pagado = false; });
  }
  private void copyRepuestoItem(RepuestoPedidoItem item, RepuestoItemRequest r) {
    if (r.fichaTrabajoId() != null) {
      if (item.pedido.ficha == null) throw new BusinessException(422, "Un ítem con trabajo requiere una ficha asociada");
      FichaTrabajo trabajo = db.get(FichaTrabajo.class, r.fichaTrabajoId());
      if (!trabajo.ficha.id.equals(item.pedido.ficha.id) || trabajo.estadoTrabajo == TrabajoState.CANCELADO) throw new BusinessException(422, "El trabajo no pertenece a una ficha válida del pedido");
      item.fichaTrabajo = trabajo;
    } else item.fichaTrabajo = null;
    RepuestoItemState next = r.estado() == null || r.estado().isBlank() ? item.estado : RepuestoItemState.of(r.estado());
    if (next != item.estado && !validRepuestoItemTransition(item.estado, next)) throw new BusinessException(422, "Transición de ítem inválida");
    item.descripcion = r.descripcion().trim(); item.tipo = r.tipo(); item.cantidad = r.cantidad() == null ? BigDecimal.ONE : r.cantidad(); item.precio = money(r.precio()); item.subtotal = money(item.cantidad.multiply(item.precio)); item.estado = next; item.observaciones = blank(r.observaciones());
    if (next == RepuestoItemState.CANCELADO) item.pagado = false;
  }
  private boolean validRepuestoItemTransition(RepuestoItemState current, RepuestoItemState next) { return (current == RepuestoItemState.PENDIENTE_DE_PEDIR && (next == RepuestoItemState.PEDIDO || next == RepuestoItemState.CANCELADO)) || (current == RepuestoItemState.PEDIDO && (next == RepuestoItemState.RECIBIDO || next == RepuestoItemState.CANCELADO)) || (current == RepuestoItemState.RECIBIDO && (next == RepuestoItemState.ENTREGADO || next == RepuestoItemState.CANCELADO)); }
  private void recalcRepuesto(RepuestoPedido e) { e.total = money(e.items.stream().filter(x -> x.estado != RepuestoItemState.CANCELADO).map(x -> x.subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)); }
  private RepuestoResponse repuesto(RepuestoPedido e) {
    List<RepuestoItemResponse> items = e.items.stream().map(i -> new RepuestoItemResponse(i.id, i.fichaTrabajo == null ? null : i.fichaTrabajo.id, i.descripcion, i.tipo, i.cantidad, i.precio, i.subtotal, i.estado.label(), i.observaciones, i.pagado)).toList();
    return new RepuestoResponse(e.id, e.numero, e.motovehiculo.id, e.motovehiculo.patente, e.cliente.id, e.cliente.nombre, e.ficha == null ? null : e.ficha.id, e.fecha, e.estado.label(), e.estadoPago.label(), e.total, e.proveedor, e.observaciones, items, e.createdAt);
  }
  public RepuestoResponse repuestoEstado(UUID id, StateRequest r) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoState next = RepuestoPedidoState.of(r.estado());
    if (next == RepuestoPedidoState.CANCELADO) e.estado = next;
    else if (next == RepuestoPedidoState.COMPLETADO && e.items.stream().allMatch(x -> x.estado == RepuestoItemState.ENTREGADO || x.estado == RepuestoItemState.CANCELADO)) e.estado = next;
    else throw new BusinessException(422, "Transición de pedido inválida");
    audit("REPUESTOS", "ESTADO", e.numero + " -> " + e.estado.label()); return repuesto(e);
  }
  public RepuestoResponse repuestoPago(UUID id, PagoRequest r) { RepuestoPedido e = db.get(RepuestoPedido.class, id); if (e.estado == RepuestoPedidoState.CANCELADO) throw new BusinessException(409, "No puede registrarse un pago en un pedido cancelado"); applyRepuestoPayment(e, PagoState.of(r.estadoPago()), r.itemIds()); audit("REPUESTOS", "PAGO", e.numero + " -> " + e.estadoPago.label()); return repuesto(e); }
  public RepuestoResponse repuestoItemEstado(UUID id, UUID itemId, StateRequest r) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    RepuestoItemState next = RepuestoItemState.of(r.estado());
    if (target.estado == RepuestoItemState.ENTREGADO || target.estado == RepuestoItemState.CANCELADO || !validRepuestoItemTransition(target.estado, next)) throw new BusinessException(422, "Transición de ítem inválida");
    target.estado = next;
    if (next == RepuestoItemState.CANCELADO) target.pagado = false;
    audit("REPUESTOS", "ITEM ESTADO", e.numero + " " + target.descripcion + " -> " + next.label());
    if (next == RepuestoItemState.ENTREGADO && e.estado == RepuestoPedidoState.EN_CURSO && e.items.stream().allMatch(x -> x.estado == RepuestoItemState.ENTREGADO || x.estado == RepuestoItemState.CANCELADO)) e.estado = RepuestoPedidoState.COMPLETADO;
    recalcRepuestoPayment(e);
    return repuesto(e);
  }

  private void applyRepuestoPayment(RepuestoPedido pedido, PagoState requested, List<UUID> selectedIds) {
    List<RepuestoPedidoItem> eligible = pedido.items.stream().filter(i -> i.estado != RepuestoItemState.CANCELADO).toList();
    if (requested == PagoState.NO_PAGADO) eligible.forEach(i -> i.pagado = false);
    else if (requested == PagoState.PAGADO) eligible.forEach(i -> i.pagado = true);
    else {
      if (eligible.isEmpty()) throw new BusinessException(422, "El pedido no tiene ítems disponibles para registrar un pago parcial");
      Set<UUID> ids = selectedIds == null ? Set.of() : new HashSet<>(selectedIds);
      Set<UUID> eligibleIds = eligible.stream().map(i -> i.id).collect(Collectors.toSet());
      if (ids.isEmpty() || !eligibleIds.containsAll(ids)) throw new BusinessException(422, "Seleccioná al menos un ítem no cancelado válido");
      eligible.forEach(i -> i.pagado = ids.contains(i.id));
    }
    recalcRepuestoPayment(pedido);
  }

  private void recalcRepuestoPayment(RepuestoPedido pedido) {
    List<RepuestoPedidoItem> eligible = pedido.items.stream().filter(i -> i.estado != RepuestoItemState.CANCELADO).toList();
    long paid = eligible.stream().filter(i -> i.pagado).count();
    pedido.estadoPago = paid == 0 ? PagoState.NO_PAGADO : paid == eligible.size() ? PagoState.PAGADO : PagoState.PARCIAL;
  }
  public RepuestoResponse updateRepuestoItem(UUID id, UUID itemId, RepuestoItemRequest r) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoItem old = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    copyRepuestoItem(old, r); recalcRepuesto(e); recalcRepuestoPayment(e);
    audit("REPUESTOS", "ÍTEM", e.numero); return repuesto(e);
  }
  public void deleteRepuestoItem(UUID id, UUID itemId) {
    RepuestoPedido e = db.get(RepuestoPedido.class, id); assertRepuestoEditable(e);
    RepuestoPedidoItem target = e.items.stream().filter(x -> x.id.equals(itemId)).findFirst().orElseThrow(() -> new NotFoundException("El ítem no existe"));
    if (e.items.stream().filter(x -> x.estado != RepuestoItemState.CANCELADO).count() <= 1) throw new BusinessException(409, "El pedido debe conservar al menos un ítem");
    target.estado = RepuestoItemState.CANCELADO; target.pagado = false; recalcRepuesto(e); recalcRepuestoPayment(e); audit("REPUESTOS", "QUITAR ÍTEM", e.numero);
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
    if (f.estado != FichaState.REVISION) throw new BusinessException(409, "La ficha debe estar en revisión");
    Revision r = findOrRestoreRevision(f);
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
    if (rev.ficha.estado != FichaState.REVISION || !trabajosFinalizados(rev.ficha)) throw new BusinessException(422, "La ficha debe tener todos los trabajos finalizados y estar en revisión");
    if (!r.forzada() && rev.controles.stream().anyMatch(c -> c.control.obligatorio && c.estado == RevisionControlState.PENDIENTE)) throw new BusinessException(422, "Faltan controles obligatorios por revisar");
    rev.estado = RevisionState.APROBADA; rev.aprobadoPor = actor(); rev.aprobadoAt = Instant.now(); rev.forzada = r.forzada(); rev.observacion = blank(r.observacion());
    Ficha f = rev.ficha;
    if (r.serviceIds() != null) for (UUID serviceId : new LinkedHashSet<>(r.serviceIds())) {
      ServiceMoto service = db.get(ServiceMoto.class, serviceId);
      if (!service.motovehiculo.id.equals(f.motovehiculo.id) || service.ficha != null) throw new BusinessException(422, "El service no puede asociarse a esta ficha");
      service.ficha = f;
    }
    f.estado = FichaState.TERMINADA;
    f.motovehiculo.estadoOperativo = MotoState.TERMINADA;
    audit("REVISION", "APROBAR", f.numero);
    return revisionDto(rev);
  }
  private Revision revisionEntity(UUID fichaId) {
    Ficha f = db.get(Ficha.class, fichaId);
    if (f.estado != FichaState.REVISION) throw new BusinessException(409, "La ficha debe estar en revisión");
    return findOrRestoreRevision(f);
  }
  private Revision findOrRestoreRevision(Ficha f) {
    Revision rev = db.one("SELECT r FROM Revision r WHERE r.ficha.id=:f", Revision.class, Map.of("f", f.id));
    if (rev == null) return createRevision(f);
    if (rev.deletedAt != null) { rev.deletedAt = null; rev.deletedBy = null; rev.estado = RevisionState.ABIERTA; rev.aprobadoPor = null; rev.aprobadoAt = null; rev.forzada = false; rev.observacion = null; }
    ensureRevisionControls(rev);
    return rev;
  }
  private Revision createRevision(Ficha f) {
    Revision rev = new Revision(); rev.ficha = f; db.persist(rev);
    ensureRevisionControls(rev);
    return rev;
  }
  private void ensureRevisionControls(Revision rev) {
    Set<UUID> existing = rev.controles.stream().map(x -> x.control.id).collect(Collectors.toSet());
    for (ControlRevision c : db.all("select c from ControlRevision c where c.deletedAt is null and c.activo=true order by c.orden", ControlRevision.class, Map.of())) {
      if (existing.contains(c.id)) continue;
      RevisionControl rc = new RevisionControl(); rc.revision = rev; rc.control = c; rev.controles.add(rc); db.persist(rc);
    }
  }
  private RevisionResponse revisionDto(Revision rev) {
    List<RevisionControlResponse> cs = rev.controles.stream().sorted(Comparator.comparingInt(x -> x.control.orden)).map(rc -> new RevisionControlResponse(rc.id, rc.control.id, rc.control.nombre, rc.control.categorias.stream().map(c -> c.nombre).collect(Collectors.joining(", ")), rc.control.obligatorio, rc.control.orden, rc.estado.label(), rc.observacion, rc.correccionNecesaria, rc.revisadoPor == null ? null : rc.revisadoPor.nombre, rc.revisadoAt)).toList();
    return new RevisionResponse(rev.id, rev.ficha.id, rev.ficha.numero, rev.estado.name(), rev.aprobadoPor == null ? null : rev.aprobadoPor.nombre, rev.aprobadoAt, rev.forzada, rev.observacion, cs);
  }

  // ---------- Configuración ----------
  public List<TrabajoCatalogoResponse> trabajos(String q, Boolean activo, boolean includeDeleted) {
    Map<String,Object> ps = p();
    String w = " where 1=1" + active(includeDeleted);
    if (q != null && !q.isBlank()) { w += " and lower(e.descripcion) like :q"; ps.put("q", "%" + q.trim().toLowerCase() + "%"); }
    if (activo != null) { w += " and e.activo=:activo"; ps.put("activo", activo); }
    return db.all("select e from TrabajoCatalogo e" + w + " order by e.descripcion", TrabajoCatalogo.class, ps).stream().map(this::trabajo).toList();
  }
  public List<TrabajoCatalogoResponse> trabajosAutocomplete(String q) {
    String query = q == null ? "" : q.trim().toLowerCase();
    return db.list("select e from TrabajoCatalogo e where e.deletedAt is null and e.activo=true and lower(e.descripcion) like :q order by e.descripcion", TrabajoCatalogo.class, Map.of("q", "%" + query + "%"), 0, 15).stream().map(this::trabajo).toList();
  }
  public TrabajoCatalogoResponse createTrabajo(TrabajoCatalogoRequest r) { TrabajoCatalogo e = new TrabajoCatalogo(); copy(r, e); db.persist(e); audit("Configuración", "CREAR TRABAJO", e.descripcion); return trabajo(e); }
  public TrabajoCatalogoResponse updateTrabajoCatalogo(UUID id, TrabajoCatalogoRequest r) { TrabajoCatalogo e = db.get(TrabajoCatalogo.class, id); copy(r, e); audit("Configuración", "EDITAR TRABAJO", e.descripcion); return trabajo(e); }
  public void deleteTrabajoCatalogo(UUID id) { TrabajoCatalogo e = db.get(TrabajoCatalogo.class, id); deleted(e); audit("Configuración", "ELIMINAR TRABAJO", e.descripcion); }
  private void copy(TrabajoCatalogoRequest r, TrabajoCatalogo e) { e.descripcion = r.descripcion().trim(); e.precioBase = money(r.precioBase()); if (r.activo() != null) e.activo = r.activo(); }
  private TrabajoCatalogoResponse trabajo(TrabajoCatalogo e) { return new TrabajoCatalogoResponse(e.id, e.descripcion, e.precioBase, e.activo, e.createdAt, e.updatedAt); }
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
  public UserResponse updateUser(UUID id, UserRequest r) {
    AppUser u = db.get(AppUser.class, id);
    boolean removesLastAdmin = u.rol == Role.ADMINISTRACION && (r.rol() != Role.ADMINISTRACION || Boolean.FALSE.equals(r.activo())) && activeAdminCount() <= 1;
    if (removesLastAdmin) throw new BusinessException(409, "Debe existir al menos un administrador activo");
    u.username = r.username().trim().toLowerCase(); u.nombre = r.nombre().trim(); u.email = blank(r.email()); u.rol = r.rol(); if (r.activo() != null) u.activo = r.activo(); if (r.password() != null && !r.password().isBlank()) u.passwordHash = encoder.encode(r.password()); audit("CONFIG", "USERS", u.username); return user(u);
  }
  public void deleteUser(UUID id) { AppUser u = db.get(AppUser.class, id); if (u.id.equals(actorId())) throw new BusinessException(409, "No puede eliminarse a sí mismo"); if (u.rol == Role.ADMINISTRACION && u.activo && activeAdminCount() <= 1) throw new BusinessException(409, "No puede eliminarse el último administrador activo"); deleted(u); audit("CONFIG", "USERS", "eliminar"); }
  private long activeAdminCount() { return db.count("select count(u) from AppUser u where u.rol=:role and u.activo=true and u.deletedAt is null", Map.of("role", Role.ADMINISTRACION)); }

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
      new ReportResponse("Motos con service", BigDecimal.valueOf(nextServices().stream().filter(row -> !row.sinReferencia()).count())),
      new ReportResponse("En proceso", BigDecimal.valueOf(db.count("select count(e) from Ficha e where e.deletedAt is null and e.estado in ('PENDIENTE','EN_PROCESO','REVISION','TERMINADA')", Map.of())))
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
    Map<String, List<TallerMotoResponse>> buckets = new LinkedHashMap<>();
    List<String> states = List.of("Ingresada Taller", "Pendiente", "En proceso", "En revisión", "Terminada", "Entregada");
    states.forEach(state -> buckets.put(state, new ArrayList<>()));
    for (Motovehiculo moto : db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true and e.seccion = com.avianto.back.MotoSection.TALLER", Motovehiculo.class, Map.of())) {
      Ficha ficha = db.one("select f from Ficha f where f.motovehiculo.id=:moto and f.deletedAt is null order by f.fechaIngreso desc nulls last, f.createdAt desc", Ficha.class, Map.of("moto", moto.id));
      String state = estadoMoto(moto);
      if (!buckets.containsKey(state)) state = moto.ingresada ? "Ingresada Taller" : "Entregada";
      buckets.get(state).add(new TallerMotoResponse(moto.id, moto.patente, moto.marca.nombre + " " + moto.modelo, propietarioActual(moto.id) == null ? null : propietarioActual(moto.id).cliente.nombre, moto.kilometraje, ficha == null ? null : ficha.id, ficha == null ? null : ficha.numero, state, ficha == null ? null : ficha.fechaIngreso));
    }
    List<TallerEstadoResponse> estados = buckets.entrySet().stream().map(entry -> new TallerEstadoResponse(entry.getKey(), entry.getValue().stream().limit(100).toList())).toList();
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
  public VentaResponse ventas() {
    Map<String, List<VentaMotoResponse>> buckets = new LinkedHashMap<>();
    List<String> states = List.of("En venta", "Transferencia en proceso", "Vendida");
    states.forEach(state -> buckets.put(state, new ArrayList<>()));
    for (Motovehiculo moto : db.all("select e from Motovehiculo e join e.marca where e.deletedAt is null and e.activo=true and e.seccion = com.avianto.back.MotoSection.VENTA", Motovehiculo.class, Map.of())) {
      String state = estadoMoto(moto);
      if (!buckets.containsKey(state)) continue;
      PropietarioMoto owner = propietarioActual(moto.id);
      buckets.get(state).add(new VentaMotoResponse(moto.id, moto.patente, moto.marca.nombre + " " + moto.modelo, owner == null ? null : owner.cliente.nombre, moto.kilometraje, state, null));
    }
    return new VentaResponse(buckets.entrySet().stream().map(entry -> new VentaEstadoResponse(entry.getKey(), entry.getValue().stream().limit(100).toList())).toList());
  }
}
