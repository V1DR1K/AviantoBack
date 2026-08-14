package com.avianto.back;

import static com.avianto.back.ApiDtos.*;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.validation.Valid;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

@RestController @RequestMapping("/api")
public class ApiController {
  private final ApiService api; private final AuthService auth;
  ApiController(ApiService api,AuthService auth){this.api=api;this.auth=auth;}
  @PostMapping("/auth/login") public SessionResponse login(@Valid @RequestBody LoginRequest r){return auth.login(r);}
  @PostMapping("/auth/refresh") public SessionResponse refresh(@Valid @RequestBody RefreshRequest r){return auth.refresh(r);}
  @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@Valid @RequestBody RefreshRequest r){auth.logout(r);}
  @GetMapping("/auth/me") public UserResponse me(){return auth.me();}

  // ---------- Clientes ----------
  @GetMapping("/clientes") public PageResponse<ClientResponse> clients(@RequestParam(required=false)String q,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="nombre")String sortBy,@RequestParam(defaultValue="ASC")String direction){return api.clients(q,activo,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/clientes") @ResponseStatus(HttpStatus.CREATED) public ClientResponse createClient(@Valid @RequestBody ClientRequest r){return api.createClient(r);}
  @GetMapping("/clientes/autocomplete") public List<AutocompleteResponse> clientAuto(@RequestParam String q){return api.clientAutocomplete(q);}
  @GetMapping("/clientes/{id}") public ClientResponse client(@PathVariable UUID id){return api.client(id);}
  @PutMapping("/clientes/{id}") public ClientResponse client(@PathVariable UUID id,@Valid @RequestBody ClientRequest r){return api.updateClient(id,r);}
  @DeleteMapping("/clientes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void clientDelete(@PathVariable UUID id){api.deleteClient(id);}

  // ---------- Motovehículos ----------
  @GetMapping("/motovehiculos") public PageResponse<MotorcycleResponse> motocicletas(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)UUID marcaId,@RequestParam(required=false)String estado,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="patente")String sortBy,@RequestParam(defaultValue="ASC")String direction){return api.motorcycles(q,clienteId,marcaId,estado,activo,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/motovehiculos") @ResponseStatus(HttpStatus.CREATED) public MotorcycleResponse createMoto(@Valid @RequestBody MotorcycleRequest r){return api.createMotorcycle(r);}
  @GetMapping("/motovehiculos/autocomplete") public List<AutocompleteResponse> motoAuto(@RequestParam String q){return api.motorcycleAutocomplete(q);}
  @GetMapping("/motovehiculos/{id}") public MotorcycleResponse moto(@PathVariable UUID id){return api.moto(id);}
  @PutMapping("/motovehiculos/{id}") public MotorcycleResponse moto(@PathVariable UUID id,@Valid @RequestBody MotorcycleRequest r){return api.updateMotorcycle(id,r);}
  @DeleteMapping("/motovehiculos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void motoDelete(@PathVariable UUID id){api.deleteMotorcycle(id);}
  @PostMapping("/motovehiculos/{id}/ingreso") public MotorcycleResponse motoIngreso(@PathVariable UUID id,@Valid @RequestBody IntakeRequest r){return api.ingresarMoto(id,r);}
  @PostMapping("/motovehiculos/{id}/venta/completar") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public MotorcycleResponse completarVenta(@PathVariable UUID id){return api.completarVenta(id);}
  @PatchMapping("/motovehiculos/{id}/config-service") public MotorcycleResponse motoConfig(@PathVariable UUID id,@Valid @RequestBody MotoConfigServiceRequest r){return api.updateMotoConfig(id,r);}

  // ---------- Perfiles (una vista integral por moto) ----------
  @GetMapping("/perfiles") public PageResponse<ProfileResponse> profiles(@RequestParam(required=false)String q,@RequestParam(required=false)String dominio,@RequestParam(required=false)String moto,@RequestParam(required=false)String cliente,@RequestParam(required=false)String estado,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="patente")String sortBy,@RequestParam(defaultValue="ASC")String direction){return api.profiles(q,dominio,moto,cliente,estado,page,size,sortBy,direction);}
  @PostMapping("/perfiles") @ResponseStatus(HttpStatus.CREATED) public ProfileResponse createProfile(@Valid @RequestBody ProfileRequest r){return api.createProfile(r);}
  @GetMapping("/perfiles/{id}") public ProfileResponse profile(@PathVariable UUID id){return api.profile(id);}

  // ---------- Propietarios ----------
  @GetMapping("/motovehiculos/{id}/propietarios") public List<OwnerResponse> owners(@PathVariable UUID id){return api.owners(id);}
  @GetMapping("/motovehiculos/{id}/transferencias") public List<TransferResponse> transfersForMotorcycle(@PathVariable UUID id){return api.transfersForMotorcycle(id);}
  @GetMapping("/transferencias") public PageResponse<TransferResponse> transfers(@RequestParam(required=false)String q,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="fechaTransferencia")String sortBy,@RequestParam(defaultValue="DESC")String direction){return api.transfers(q,fechaDesde,fechaHasta,page,size,sortBy,direction);}
  @PostMapping("/transferencias") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public TransferResponse createTransfer(@Valid @RequestBody TransferRequest r){return api.createTransfer(r);}
  @PutMapping("/transferencias/{id}") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public TransferResponse updateTransfer(@PathVariable UUID id,@Valid @RequestBody TransferUpdateRequest r){return api.updateTransfer(id,r);}
  @DeleteMapping("/transferencias/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public void transferDelete(@PathVariable UUID id){api.deleteTransfer(id);}

  // ---------- Service ----------
  @GetMapping("/motovehiculos/{id}/services") public List<ServiceResponse> services(@PathVariable UUID id){return api.services(id);}
  @GetMapping("/motovehiculos/{id}/services/historial") public PageResponse<ServiceResponse> serviceHistory(@PathVariable UUID id,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size,@RequestParam(defaultValue="fecha")String sortBy,@RequestParam(defaultValue="DESC")String direction){return api.serviceHistory(id,fechaDesde,fechaHasta,page,size,sortBy,direction);}
  @PostMapping("/motovehiculos/{id}/services") @ResponseStatus(HttpStatus.CREATED) public ServiceResponse addService(@PathVariable UUID id,@Valid @RequestBody ServiceRequest r){return api.addService(id,r);}
  @GetMapping("/services/proximos") public List<NextServiceResponse> nextServices(){return api.nextServices();}

  // ---------- Fichas de trabajo ----------
  @GetMapping("/fichas") public PageResponse<FichaResponse> fichas(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)UUID motoId,@RequestParam(required=false)String patente,@RequestParam(required=false)String estado,@RequestParam(required=false)String estadoPago,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="fechaIngreso")String sortBy,@RequestParam(defaultValue="DESC")String direction){return api.fichas(q,clienteId,motoId,patente,estado,estadoPago,fechaDesde,fechaHasta,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/fichas") @ResponseStatus(HttpStatus.CREATED) public FichaResponse createFicha(@Valid @RequestBody FichaRequest r){return api.createFicha(r);}
  @GetMapping("/fichas/{id}") public FichaResponse ficha(@PathVariable UUID id){return api.ficha(id);}
  @PutMapping("/fichas/{id}") public FichaResponse updateFicha(@PathVariable UUID id,@Valid @RequestBody FichaRequest r){return api.updateFicha(id,r);}
  @DeleteMapping("/fichas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void fichaDelete(@PathVariable UUID id){api.deleteFicha(id);}
  @PatchMapping("/fichas/{id}/estado") public FichaResponse fichaState(@PathVariable UUID id,@Valid @RequestBody StateRequest r){return api.fichaState(id,r);}
  @PostMapping("/fichas/{id}/entregar") public FichaResponse fichaEntrega(@PathVariable UUID id){return api.entregarFicha(id);}
  @GetMapping("/fichas/{id}/pagos") public List<PagoResponse> fichaPagos(@PathVariable UUID id){return api.fichaPagos(id);}
  @PostMapping("/fichas/{id}/pagos") @ResponseStatus(HttpStatus.CREATED) public PagoResponse fichaPago(@PathVariable UUID id,@Valid @RequestBody PagoRegistroRequest r){return api.registrarFichaPago(id,r);}
  @PostMapping("/fichas/{id}/pagos/{pagoId}/anular") public PagoResponse anularFichaPago(@PathVariable UUID id,@PathVariable UUID pagoId){return api.anularFichaPago(id,pagoId);}
  @GetMapping("/fichas/{id}/repuestos") public List<RepuestoResponse> fichaRepuestos(@PathVariable UUID id){return api.repuestosFicha(id);}

  @GetMapping("/fichas/{id}/trabajos") public List<FichaTrabajoResponse> fichaTrabajos(@PathVariable UUID id){return api.ficha(id).trabajos();}
  @PostMapping("/fichas/{id}/trabajos") @ResponseStatus(HttpStatus.CREATED) public FichaResponse addTrabajo(@PathVariable UUID id,@Valid @RequestBody FichaTrabajoRequest r){return api.addTrabajo(id,r);}
  @PutMapping("/fichas/{id}/trabajos/{trabajoId}") public FichaResponse updateTrabajo(@PathVariable UUID id,@PathVariable UUID trabajoId,@Valid @RequestBody FichaTrabajoRequest r){return api.updateTrabajo(id,trabajoId,r);}
  @DeleteMapping("/fichas/{id}/trabajos/{trabajoId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void trabajoDelete(@PathVariable UUID id,@PathVariable UUID trabajoId){api.deleteTrabajo(id,trabajoId);}
  @PatchMapping("/fichas/{id}/trabajos/{trabajoId}/estado") public FichaResponse trabajoState(@PathVariable UUID id,@PathVariable UUID trabajoId,@Valid @RequestBody StateRequest r){return api.trabajoState(id,trabajoId,r);}

  @PostMapping("/fichas/{id}/fotos") @ResponseStatus(HttpStatus.CREATED) public PhotoResponse photo(@PathVariable UUID id,@Valid @RequestBody PhotoRequest r){return api.createPhoto(id,r);}
  @GetMapping("/fichas/{id}/fotos/{fotoId}") public ResponseEntity<byte[]> photo(@PathVariable UUID id,@PathVariable UUID fotoId){FichaFoto photo=api.photo(id,fotoId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType)).body(photo.content);}
  @GetMapping(value="/fichas/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE) public ResponseEntity<byte[]> pdf(@PathVariable UUID id)throws Exception{return attachment("ficha-"+id+".pdf",MediaType.APPLICATION_PDF,pdf(api.ficha(id),api.repuestosFicha(id)));}

  // ---------- Revisión final de entrega ----------
  @GetMapping("/fichas/{id}/revision") public RevisionResponse revision(@PathVariable UUID id){return api.revision(id);}
  @PatchMapping("/fichas/{id}/revision/controles/{controlId}") public RevisionResponse revisionControl(@PathVariable UUID id,@PathVariable UUID controlId,@Valid @RequestBody RevisionControlRequest r){return api.updateControlEstado(id,controlId,r);}
  @PostMapping("/fichas/{id}/revision/aprobar") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public RevisionResponse aprobarRevision(@PathVariable UUID id,@Valid @RequestBody RevisionAprobarRequest r){return api.aprobarRevision(id,r);}

  // ---------- Pedidos de repuestos (piezas y accesorios) ----------
  @GetMapping("/repuestos") public PageResponse<RepuestoResponse> repuestos(@RequestParam(required=false)String estado,@RequestParam(required=false)String estadoPago,@RequestParam(required=false)UUID motoId,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)String q,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="fecha")String sortBy,@RequestParam(defaultValue="DESC")String direction){return api.repuestos(estado,estadoPago,motoId,clienteId,q,fechaDesde,fechaHasta,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/repuestos") @ResponseStatus(HttpStatus.CREATED) public RepuestoResponse createRepuesto(@Valid @RequestBody RepuestoRequest r){return api.createRepuesto(r);}
  @GetMapping("/repuestos/{id}") public RepuestoResponse repuesto(@PathVariable UUID id){return api.repuesto(id);}
  @PutMapping("/repuestos/{id}") public RepuestoResponse updateRepuesto(@PathVariable UUID id,@Valid @RequestBody RepuestoRequest r){return api.updateRepuesto(id,r);}
  @DeleteMapping("/repuestos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void repuestoDelete(@PathVariable UUID id){api.deleteRepuesto(id);}
  @PatchMapping("/repuestos/{id}/estado") public RepuestoResponse repuestoState(@PathVariable UUID id,@Valid @RequestBody StateRequest r){return api.repuestoEstado(id,r);}
  @GetMapping("/repuestos/{id}/pagos") public List<PagoResponse> repuestoPagos(@PathVariable UUID id){return api.repuestoPagos(id);}
  @PostMapping("/repuestos/{id}/pagos") @ResponseStatus(HttpStatus.CREATED) public PagoResponse repuestoPago(@PathVariable UUID id,@Valid @RequestBody PagoRegistroRequest r){return api.registrarRepuestoPago(id,r);}
  @PostMapping("/repuestos/{id}/pagos/{pagoId}/anular") public PagoResponse anularRepuestoPago(@PathVariable UUID id,@PathVariable UUID pagoId){return api.anularRepuestoPago(id,pagoId);}
  @PatchMapping("/repuestos/{id}/items/{itemId}/estado") public RepuestoResponse repuestoItemState(@PathVariable UUID id,@PathVariable UUID itemId,@Valid @RequestBody StateRequest r){return api.repuestoItemEstado(id,itemId,r);}
  @PutMapping("/repuestos/{id}/items/{itemId}") public RepuestoResponse updateRepuestoItem(@PathVariable UUID id,@PathVariable UUID itemId,@Valid @RequestBody RepuestoItemRequest r){return api.updateRepuestoItem(id,itemId,r);}
  @DeleteMapping("/repuestos/{id}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void repuestoItemDelete(@PathVariable UUID id,@PathVariable UUID itemId){api.deleteRepuestoItem(id,itemId);}

  // ---------- Auditoría / reportes / dashboard ----------
  @GetMapping("/auditoria") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public List<AuditResponse> audit(@RequestParam(required=false)String q,@RequestParam(required=false)UUID usuarioId,@RequestParam(required=false)String modulo,@RequestParam(required=false)String accion,@RequestParam(required=false)Instant fechaDesde,@RequestParam(required=false)Instant fechaHasta){return api.audits(q,usuarioId,modulo,accion,fechaDesde,fechaHasta);}
  @GetMapping("/reportes/resumen") public List<ReportResponse> summary(){return api.summary();}
  @GetMapping("/reportes/evolucion") public List<ReportResponse> evolution(){return api.evolution();}
  @GetMapping("/dashboard") public DashboardResponse dashboard(@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta){return api.dashboard(fechaDesde,fechaHasta);}
  @GetMapping("/dashboard/taller") public TallerResponse taller(){return api.taller();}
  @GetMapping("/dashboard/fichas") public DashboardFichasResponse dashboardFichas(){return api.dashboardFichas();}
  @GetMapping("/dashboard/ventas") public VentaResponse ventas(){return api.ventas();}

  // ---------- Configuración ----------
  @GetMapping("/configuracion/trabajos") public List<TrabajoCatalogoResponse> trabajos(@RequestParam(required=false)String q, @RequestParam(required=false)Boolean activo, @RequestParam(defaultValue="false")boolean includeDeleted){return api.trabajos(q,activo,includeDeleted);}
  @GetMapping("/configuracion/trabajos/autocomplete") public List<TrabajoCatalogoResponse> trabajosAutocomplete(@RequestParam(defaultValue="") String q){return api.trabajosAutocomplete(q);}
  @PostMapping("/configuracion/trabajos") @ResponseStatus(HttpStatus.CREATED) public TrabajoCatalogoResponse createTrabajo(@Valid @RequestBody TrabajoCatalogoRequest r){return api.createTrabajo(r);}
  @PutMapping("/configuracion/trabajos/{id}") public TrabajoCatalogoResponse updateTrabajo(@PathVariable UUID id, @Valid @RequestBody TrabajoCatalogoRequest r){return api.updateTrabajoCatalogo(id,r);}
  @DeleteMapping("/configuracion/trabajos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteTrabajo(@PathVariable UUID id){api.deleteTrabajoCatalogo(id);}
  @GetMapping("/configuracion/controles") public List<ControlResponse> controls(@RequestParam(defaultValue="false")boolean includeDeleted){return api.controls(includeDeleted);}
  @PostMapping("/configuracion/controles") @ResponseStatus(HttpStatus.CREATED) public ControlResponse control(@Valid @RequestBody ControlRequest r){return api.createControl(r);}
  @PutMapping("/configuracion/controles/{id}") public ControlResponse control(@PathVariable UUID id,@Valid @RequestBody ControlRequest r){return api.updateControl(id,r);}
  @DeleteMapping("/configuracion/controles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void controlDelete(@PathVariable UUID id){api.deleteControl(id);}

  @GetMapping("/configuracion/marcas-moto") public List<NamedResponse> brands(@RequestParam(defaultValue="false")boolean includeDeleted){return api.brands(includeDeleted);}
  @PostMapping("/configuracion/marcas-moto") @ResponseStatus(HttpStatus.CREATED) public NamedResponse createBrand(@Valid @RequestBody NameRequest r){return api.createBrand(r);}
  @PutMapping("/configuracion/marcas-moto/{id}") public NamedResponse updateBrand(@PathVariable UUID id,@Valid @RequestBody NameRequest r){return api.updateBrand(id,r);}
  @DeleteMapping("/configuracion/marcas-moto/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void brandDelete(@PathVariable UUID id){api.deleteBrand(id);}

  @GetMapping("/configuracion/categorias") public List<NamedResponse> categorias(@RequestParam(defaultValue="false")boolean includeDeleted){return api.categorias(includeDeleted);}
  @PostMapping("/configuracion/categorias") @ResponseStatus(HttpStatus.CREATED) public NamedResponse createCategoria(@Valid @RequestBody NameRequest r){return api.createCategoria(r);}
  @PutMapping("/configuracion/categorias/{id}") public NamedResponse updateCategoria(@PathVariable UUID id,@Valid @RequestBody NameRequest r){return api.updateCategoria(id,r);}
  @DeleteMapping("/configuracion/categorias/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void categoriaDelete(@PathVariable UUID id){api.deleteCategoria(id);}

  @GetMapping("/configuracion/usuarios") public List<UserResponse> users(@RequestParam(defaultValue="false")boolean includeDeleted){return api.users(includeDeleted);}
  @PostMapping("/configuracion/usuarios") @ResponseStatus(HttpStatus.CREATED) public UserResponse createUser(@Valid @RequestBody UserRequest r){return api.createUser(r);}
  @PutMapping("/configuracion/usuarios/{id}") public UserResponse updateUser(@PathVariable UUID id,@Valid @RequestBody UserRequest r){return api.updateUser(id,r);}
  @DeleteMapping("/configuracion/usuarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void userDelete(@PathVariable UUID id){api.deleteUser(id);}

  // ---------- Exports ----------
  @GetMapping("/clientes/export.xlsx") public ResponseEntity<byte[]> clientExport(@RequestParam(required=false)String q,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="nombre")String sortBy,@RequestParam(defaultValue="ASC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("clientes",all(n->api.clients(q,activo,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/motovehiculos/export.xlsx") public ResponseEntity<byte[]> motoExport(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)UUID marcaId,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="patente")String sortBy,@RequestParam(defaultValue="ASC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("motovehiculos",all(n->api.motorcycles(q,clienteId,marcaId,null,activo,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/fichas/export.xlsx") public ResponseEntity<byte[]> fichasExport(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)UUID motoId,@RequestParam(required=false)String estado,@RequestParam(required=false)String estadoPago,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="fechaIngreso")String sortBy,@RequestParam(defaultValue="DESC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("fichas",all(n->api.fichas(q,clienteId,motoId,null,estado,estadoPago,fechaDesde,fechaHasta,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/repuestos/export.xlsx") public ResponseEntity<byte[]> repuestosExport(@RequestParam(required=false)String estado,@RequestParam(required=false)String estadoPago,@RequestParam(required=false)UUID motoId,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="fecha")String sortBy,@RequestParam(defaultValue="DESC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("repuestos",all(n->api.repuestos(estado,estadoPago,motoId,clienteId,null,fechaDesde,fechaHasta,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/transferencias/export.xlsx") public ResponseEntity<byte[]> transfersExport(@RequestParam(required=false)String q,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="fechaTransferencia")String sortBy,@RequestParam(defaultValue="DESC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("transferencias",all(n->api.transfers(q,fechaDesde,fechaHasta,n,100,sortBy,direction).content()),columns);}

  private <T> List<T> all(Function<Integer,List<T>> get){List<T> r=new ArrayList<>();for(int p=0;;p++){List<T> part=get.apply(p);r.addAll(part);if(part.size()<100)return r;}}
  private ResponseEntity<byte[]> excel(String name,List<?> rows,String ignored)throws Exception{
    record Col(String header,String kind,Function<Object,String> value){}
    List<Col> cols;
    switch(name){
      case "clientes" -> cols=List.of(
        new Col("Nombre","text",o->str(((ClientResponse)o).nombre())),
        new Col("Documento","text",o->str(((ClientResponse)o).documento())),
        new Col("Teléfono","text",o->str(((ClientResponse)o).telefono())),
        new Col("Email","text",o->str(((ClientResponse)o).email())),
        new Col("Dirección","text",o->str(((ClientResponse)o).direccion())),
        new Col("Estado","text",o->((ClientResponse)o).activo()?"Activo":"Baja"),
        new Col("Motos","number",o->String.valueOf(((ClientResponse)o).motos())));
      case "motovehiculos" -> cols=List.of(
        new Col("Patente","text",o->str(((MotorcycleResponse)o).patente())),
        new Col("Marca","text",o->str(((MotorcycleResponse)o).marca())),
        new Col("Modelo","text",o->str(((MotorcycleResponse)o).modelo())),
        new Col("Propietario","text",o->str(((MotorcycleResponse)o).propietario())),
        new Col("Año","number",o->((MotorcycleResponse)o).anio()==null?"":String.valueOf(((MotorcycleResponse)o).anio())),
        new Col("Kilómetros","number",o->((MotorcycleResponse)o).kilometraje()==null?"":String.valueOf(((MotorcycleResponse)o).kilometraje())),
       new Col("Estado","text",o->str(((MotorcycleResponse)o).estado())));
       case "transferencias" -> cols=List.of(
         new Col("Patente","text",o->str(((TransferResponse)o).patente())),
         new Col("Moto","text",o->str(((TransferResponse)o).moto())),
         new Col("Fecha","date",o->dateVal(((TransferResponse)o).fechaTransferencia())),
         new Col("Cliente anterior","text",o->str(((TransferResponse)o).clienteAnterior())),
         new Col("Cliente nuevo","text",o->str(((TransferResponse)o).clienteNuevo())),
         new Col("Observaciones","text",o->str(((TransferResponse)o).observaciones())));
       case "fichas" -> cols=List.of(
        new Col("Número","text",o->str(((FichaResponse)o).numero())),
        new Col("Cliente","text",o->str(((FichaResponse)o).cliente())),
        new Col("Moto","text",o->str(((FichaResponse)o).moto())),
        new Col("Patente","text",o->str(((FichaResponse)o).patente())),
        new Col("Ingreso","date",o->dateVal(((FichaResponse)o).fechaIngreso())),
        new Col("Estimada","date",o->dateVal(((FichaResponse)o).fechaEntregaEstimada())),
        new Col("Estado","text",o->str(((FichaResponse)o).estado())),
        new Col("Pago","text",o->str(((FichaResponse)o).estadoPago())),
        new Col("Total","number",o->((FichaResponse)o).total().toPlainString()),
        new Col("Trabajos","text",o->fichaTrabajos(((FichaResponse)o).trabajos())));
      default -> cols=List.of(
        new Col("Número","text",o->str(((RepuestoResponse)o).numero())),
        new Col("Fecha","date",o->dateVal(((RepuestoResponse)o).fecha())),
        new Col("Cliente","text",o->str(((RepuestoResponse)o).cliente())),
        new Col("Patente","text",o->str(((RepuestoResponse)o).patente())),
        new Col("Proveedor","text",o->str(((RepuestoResponse)o).proveedor())),
        new Col("Estado","text",o->str(((RepuestoResponse)o).estado())),
        new Col("Pago","text",o->str(((RepuestoResponse)o).estadoPago())),
        new Col("Total","number",o->((RepuestoResponse)o).total().toPlainString()),
        new Col("Ítems","text",o->repuestoItems(((RepuestoResponse)o).items())));
    }
    try(XSSFWorkbook wb=new XSSFWorkbook()){
      var sheet=wb.createSheet(name);
      var header=sheet.createRow(0);
      for(int c=0;c<cols.size();c++)header.createCell(c).setCellValue(cols.get(c).header());
      for(int r=0;r<rows.size();r++){
        var row=sheet.createRow(r+1);
        for(int c=0;c<cols.size();c++){
          String v=cols.get(c).value().apply(rows.get(r));
          if("number".equals(cols.get(c).kind())){try{row.createCell(c).setCellValue(Double.parseDouble(v.replace(',','.')));}catch(Exception e){row.createCell(c).setCellValue(v);}}
          else row.createCell(c).setCellValue(v);
        }
      }
      for(int c=0;c<cols.size();c++)sheet.autoSizeColumn(c);
      try(ByteArrayOutputStream out=new ByteArrayOutputStream()){wb.write(out);return attachment(name+".xlsx",MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),out.toByteArray());}
    }}
  private String str(String v){return v==null||v.isBlank()?"":v.trim();}
  private String dateVal(LocalDate d){return d==null?"":d.toString();}
  private String fichaTrabajos(List<FichaTrabajoResponse> items){if(items==null||items.isEmpty())return "";return String.join(" | ",items.stream().map(i->i.descripcion()).toList());}
  private String repuestoItems(List<RepuestoItemResponse> items){if(items==null||items.isEmpty())return "—";return String.join(" | ",items.stream().map(i->DecimalFormat(i.cantidad())+"x "+i.descripcion()).toList());}
  private String DecimalFormat(BigDecimal value){return value==null?"0":value.stripTrailingZeros().toPlainString();}
  private byte[] pdf(FichaResponse ficha,List<RepuestoResponse> repuestos)throws Exception{
    Font title=new Font(Font.HELVETICA,18,Font.BOLD), heading=new Font(Font.HELVETICA,12,Font.BOLD), body=new Font(Font.HELVETICA,9), muted=new Font(Font.HELVETICA,8,Font.NORMAL);
    ByteArrayOutputStream out=new ByteArrayOutputStream(); Document document=new Document(); PdfWriter.getInstance(document,out); document.open();
    Paragraph brand=new Paragraph("AVIANTO",title); brand.setAlignment(Element.ALIGN_LEFT); document.add(brand);
    document.add(new Paragraph("Resumen de ficha de trabajo",heading));
    document.add(new Paragraph("Ficha "+ficha.numero()+" · Estado: "+ficha.estado()+" · Pago: "+ficha.estadoPago(),body));
    document.add(new Paragraph(" "));
    PdfPTable datos=table(new float[]{1f,1f},heading,"Cliente","Motovehículo");
    cell(datos,"Cliente\n"+text(ficha.cliente()),body); cell(datos,"Moto\n"+text(ficha.moto()),body);
    cell(datos,"Dominio\n"+text(ficha.patente()),body); cell(datos,"Ingreso\n"+date(ficha.fechaIngreso()),body);
    cell(datos,"Entrega estimada\n"+date(ficha.fechaEntregaEstimada()),body); cell(datos,"Kilometraje de ingreso\n"+(ficha.kilometrajeIngreso()==null?"—":ficha.kilometrajeIngreso()+" km"),body);
    document.add(datos);
    if(ficha.observaciones()!=null&&!ficha.observaciones().isBlank()){
      document.add(new Paragraph("Observaciones",heading)); document.add(new Paragraph(ficha.observaciones(),body));
    }
    document.add(new Paragraph("Trabajos y servicios",heading));
    PdfPTable trabajos=table(new float[]{3.2f,1.1f,1.1f,1.1f,1.1f},heading,"Descripción","Estado","Precio","Desc.","Subtotal");
    for(FichaTrabajoResponse trabajo:ficha.trabajos()){
      cell(trabajos,text(trabajo.descripcion()),body); cell(trabajos,text(trabajo.estadoTrabajo()),body); cell(trabajos,currency(trabajo.precioUnitario()),body); cell(trabajos,currency(trabajo.descuento()),body); cell(trabajos,currency(trabajo.subtotal()),body);
      if(trabajo.observacionTrabajo()!=null&&!trabajo.observacionTrabajo().isBlank()) cellSpan(trabajos,"Observación: "+trabajo.observacionTrabajo(),muted,5);
    }
    document.add(trabajos);
    BigDecimal subtotalTrabajos=ficha.trabajos().stream().filter(trabajo->!"Cancelado".equals(trabajo.estadoTrabajo())).map(FichaTrabajoResponse::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add);
    BigDecimal despuesDescuento=subtotalTrabajos.subtract(ficha.descuentoGlobal()).max(BigDecimal.ZERO);
    BigDecimal iva=ficha.iva()?ficha.total().subtract(despuesDescuento):BigDecimal.ZERO;
    document.add(new Paragraph("Subtotal trabajos: "+currency(subtotalTrabajos),body));
    if(ficha.descuentoGlobal().signum()>0) document.add(new Paragraph("Descuento global: "+currency(ficha.descuentoGlobal()),body));
    if(ficha.iva()) document.add(new Paragraph("IVA 21%: "+currency(iva),body));
    document.add(new Paragraph("Total ficha: "+currency(ficha.total()),heading));
    document.add(new Paragraph(" "));
    document.add(new Paragraph("Pedidos de repuestos y accesorios vinculados",heading));
    BigDecimal totalRepuestos=BigDecimal.ZERO;
    if(repuestos.isEmpty()) document.add(new Paragraph("No hay pedidos vinculados a esta ficha.",muted));
    for(RepuestoResponse pedido:repuestos){
      document.add(new Paragraph(pedido.numero()+" · "+pedido.estado()+" · Proveedor: "+text(pedido.proveedor()),body));
      if(pedido.observaciones()!=null&&!pedido.observaciones().isBlank()) document.add(new Paragraph("Observación del pedido: "+pedido.observaciones(),muted));
      PdfPTable items=table(new float[]{2.7f,1f,.7f,1.2f,1f,1.1f},heading,"Ítem","Tipo","Cant.","Estado","Precio","Subtotal");
      for(RepuestoItemResponse item:pedido.items()){
        cell(items,text(item.descripcion()),body); cell(items,item.tipo().label(),body); cell(items,DecimalFormat(item.cantidad()),body); cell(items,text(item.estado()),body); cell(items,currency(item.precio()),body); cell(items,currency(item.subtotal()),body);
        if(item.observaciones()!=null&&!item.observaciones().isBlank()) cellSpan(items,"Observación: "+item.observaciones(),muted,6);
      }
      document.add(items);
      if(!"Cancelado".equals(pedido.estado())) totalRepuestos=totalRepuestos.add(pedido.total());
    }
    document.add(new Paragraph("Total repuestos vinculados: "+currency(totalRepuestos),body));
    document.add(new Paragraph("TOTAL PRESUPUESTO: "+currency(ficha.total().add(totalRepuestos)),title));
    document.close(); return out.toByteArray();
  }
  private PdfPTable table(float[] widths,Font font,String... headers)throws Exception{PdfPTable table=new PdfPTable(widths);table.setWidthPercentage(100);for(String header:headers){PdfPCell cell=new PdfPCell(new Phrase(header,font));cell.setHorizontalAlignment(Element.ALIGN_CENTER);table.addCell(cell);}return table;}
  private void cell(PdfPTable table,String value,Font font){table.addCell(new PdfPCell(new Phrase(value,font)));}
  private void cellSpan(PdfPTable table,String value,Font font,int columns){PdfPCell cell=new PdfPCell(new Phrase(value,font));cell.setColspan(columns);table.addCell(cell);}
  private String currency(BigDecimal value){return "$ "+(value==null?BigDecimal.ZERO:value).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
  private String date(LocalDate value){return value==null?"—":value.toString();}
  private String text(String value){return value==null||value.isBlank()?"—":value.trim();}
  private ResponseEntity<byte[]> attachment(String name,MediaType type,byte[] body){return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(body);}
}
