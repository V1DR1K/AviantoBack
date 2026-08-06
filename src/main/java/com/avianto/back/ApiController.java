package com.avianto.back;

import static com.avianto.back.ApiDtos.*;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
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

  @GetMapping("/clientes") public PageResponse<ClientResponse> clients(@RequestParam(required=false)String q,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="nombre")String sortBy,@RequestParam(defaultValue="ASC")String direction){return api.clients(q,activo,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/clientes") @ResponseStatus(HttpStatus.CREATED) public ClientResponse createClient(@Valid @RequestBody ClientRequest r){return api.createClient(r);}
  @GetMapping("/clientes/autocomplete") public List<AutocompleteResponse> clientAuto(@RequestParam String q){return api.clientAutocomplete(q);}
  @GetMapping("/clientes/{id}") public ClientResponse client(@PathVariable UUID id){return api.client(id);}
  @PutMapping("/clientes/{id}") public ClientResponse client(@PathVariable UUID id,@Valid @RequestBody ClientRequest r){return api.updateClient(id,r);}
  @DeleteMapping("/clientes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void clientDelete(@PathVariable UUID id){api.deleteClient(id);}

  @GetMapping("/motovehiculos") public PageResponse<MotorcycleResponse> motorcycles(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)UUID marcaId,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="patente")String sortBy,@RequestParam(defaultValue="ASC")String direction){return api.motorcycles(q,clienteId,marcaId,activo,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/motovehiculos") @ResponseStatus(HttpStatus.CREATED) public MotorcycleResponse createMoto(@Valid @RequestBody MotorcycleRequest r){return api.createMotorcycle(r);}
  @GetMapping("/motovehiculos/autocomplete") public List<AutocompleteResponse> motoAuto(@RequestParam String q){return api.motorcycleAutocomplete(q);}
  @GetMapping("/motovehiculos/{id}") public MotorcycleResponse moto(@PathVariable UUID id){return api.motorcycle(id);}
  @PutMapping("/motovehiculos/{id}") public MotorcycleResponse moto(@PathVariable UUID id,@Valid @RequestBody MotorcycleRequest r){return api.updateMotorcycle(id,r);}
  @DeleteMapping("/motovehiculos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void motoDelete(@PathVariable UUID id){api.deleteMotorcycle(id);}

  @GetMapping("/catalogo-items") public PageResponse<CatalogResponse> catalog(@RequestParam(required=false)String q,@RequestParam(required=false)ItemType tipo,@RequestParam(required=false)UUID categoriaId,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="descripcion")String sortBy,@RequestParam(defaultValue="ASC")String direction){return api.catalog(q,tipo,categoriaId,activo,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/catalogo-items") @ResponseStatus(HttpStatus.CREATED) public CatalogResponse createCatalog(@Valid @RequestBody CatalogRequest r){return api.createCatalog(r);}
  @GetMapping("/catalogo-items/{id}") public CatalogResponse catalog(@PathVariable UUID id){return api.catalog(id);}
  @PutMapping("/catalogo-items/{id}") public CatalogResponse catalog(@PathVariable UUID id,@Valid @RequestBody CatalogRequest r){return api.updateCatalog(id,r);}
  @DeleteMapping("/catalogo-items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void catalogDelete(@PathVariable UUID id){api.deleteCatalog(id);}
  @GetMapping("/catalogo-items/{id}/price-history") public List<Map<String,Object>> history(@PathVariable UUID id){return api.priceHistory(id);}
  @GetMapping("/catalogo-items/duplicates") public List<CatalogResponse> duplicateItems(@RequestParam String descripcion){return api.duplicates(descripcion);}

  @GetMapping("/pedidos") public PageResponse<OrderResponse> orders(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)String patente,@RequestParam(required=false)String numero,@RequestParam(required=false)String estado,@RequestParam(required=false)String documento,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="createdAt")String sortBy,@RequestParam(defaultValue="DESC")String direction){return api.orders(q,clienteId,patente,numero,estado,documento,fechaDesde,fechaHasta,includeDeleted,page,size,sortBy,direction);}
  @PostMapping("/pedidos") @ResponseStatus(HttpStatus.CREATED) public OrderResponse createOrder(@Valid @RequestBody OrderRequest r){return api.createOrder(r);}
  @GetMapping("/pedidos/{id}") public OrderResponse order(@PathVariable UUID id){return api.order(id);}
  @PutMapping("/pedidos/{id}") public OrderResponse order(@PathVariable UUID id,@Valid @RequestBody OrderRequest r){return api.updateOrder(id,r);}
  @DeleteMapping("/pedidos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void orderDelete(@PathVariable UUID id){api.deleteOrder(id);}
  @PatchMapping("/pedidos/{id}/estado") public OrderResponse state(@PathVariable UUID id,@Valid @RequestBody StateRequest r){return api.state(id,r);}
  @PostMapping("/pedidos/{id}/duplicate") @ResponseStatus(HttpStatus.CREATED) public OrderResponse duplicate(@PathVariable UUID id){return api.duplicate(id);}
  @PostMapping("/pedidos/{id}/fotos") @ResponseStatus(HttpStatus.CREATED) public PhotoResponse photo(@PathVariable UUID id,@Valid @RequestBody PhotoRequest r){return api.photo(id,r);}
  @GetMapping(value="/pedidos/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE) public ResponseEntity<byte[]> pdf(@PathVariable UUID id)throws Exception{return attachment("pedido-"+id+".pdf",MediaType.APPLICATION_PDF,pdf(api.order(id)));}

  @GetMapping("/auditoria") @PreAuthorize("hasAuthority('ROLE_ADMINISTRACION')") public List<AuditResponse> audit(@RequestParam(required=false)String q,@RequestParam(required=false)UUID usuarioId,@RequestParam(required=false)String modulo,@RequestParam(required=false)String accion,@RequestParam(required=false)Instant fechaDesde,@RequestParam(required=false)Instant fechaHasta){return api.audits(q,usuarioId,modulo,accion,fechaDesde,fechaHasta);}
  @GetMapping("/reportes/resumen") public List<ReportResponse> summary(){return api.summary();}
  @GetMapping("/reportes/evolucion") public List<ReportResponse> evolution(){return api.evolution();}
  @GetMapping("/reportes/top-items") public List<ReportResponse> topItems(){return api.topItems();}

  @GetMapping("/configuracion/marcas-moto") public List<NamedResponse> brands(@RequestParam(defaultValue="false")boolean includeDeleted){return api.brands(includeDeleted);}
  @PostMapping("/configuracion/marcas-moto") @ResponseStatus(HttpStatus.CREATED) public NamedResponse brand(@Valid @RequestBody NameRequest r){return api.createBrand(r);}
  @PutMapping("/configuracion/marcas-moto/{id}") public NamedResponse brand(@PathVariable UUID id,@Valid @RequestBody NameRequest r){return api.updateBrand(id,r);}
  @DeleteMapping("/configuracion/marcas-moto/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void brandDelete(@PathVariable UUID id){api.deleteBrand(id);}
  @GetMapping("/configuracion/categorias-catalogo") public List<NamedResponse> categories(@RequestParam(defaultValue="false")boolean includeDeleted){return api.categories(includeDeleted);}
  @PostMapping("/configuracion/categorias-catalogo") @ResponseStatus(HttpStatus.CREATED) public NamedResponse category(@Valid @RequestBody NameRequest r){return api.createCategory(r);}
  @PutMapping("/configuracion/categorias-catalogo/{id}") public NamedResponse category(@PathVariable UUID id,@Valid @RequestBody NameRequest r){return api.updateCategory(id,r);}
  @DeleteMapping("/configuracion/categorias-catalogo/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void categoryDelete(@PathVariable UUID id){api.deleteCategory(id);}
  @GetMapping("/configuracion/usuarios") public List<UserResponse> users(@RequestParam(defaultValue="false")boolean includeDeleted){return api.users(includeDeleted);}
  @PostMapping("/configuracion/usuarios") @ResponseStatus(HttpStatus.CREATED) public UserResponse user(@Valid @RequestBody UserRequest r){return api.createUser(r);}
  @PutMapping("/configuracion/usuarios/{id}") public UserResponse user(@PathVariable UUID id,@Valid @RequestBody UserRequest r){return api.updateUser(id,r);}
  @DeleteMapping("/configuracion/usuarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void userDelete(@PathVariable UUID id){api.deleteUser(id);}

  @GetMapping("/clientes/export.xlsx") public ResponseEntity<byte[]> clientExport(@RequestParam(required=false)String q,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="nombre")String sortBy,@RequestParam(defaultValue="ASC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("clientes",all(n->api.clients(q,activo,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/motovehiculos/export.xlsx") public ResponseEntity<byte[]> motoExport(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)UUID marcaId,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="patente")String sortBy,@RequestParam(defaultValue="ASC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("motovehiculos",all(n->api.motorcycles(q,clienteId,marcaId,activo,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/catalogo-items/export.xlsx") public ResponseEntity<byte[]> catalogExport(@RequestParam(required=false)String q,@RequestParam(required=false)ItemType tipo,@RequestParam(required=false)UUID categoriaId,@RequestParam(required=false)Boolean activo,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="descripcion")String sortBy,@RequestParam(defaultValue="ASC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("catalogo",all(n->api.catalog(q,tipo,categoriaId,activo,includeDeleted,n,100,sortBy,direction).content()),columns);}
  @GetMapping("/pedidos/export.xlsx") public ResponseEntity<byte[]> orderExport(@RequestParam(required=false)String q,@RequestParam(required=false)UUID clienteId,@RequestParam(required=false)String patente,@RequestParam(required=false)String numero,@RequestParam(required=false)String estado,@RequestParam(required=false)String documento,@RequestParam(required=false)LocalDate fechaDesde,@RequestParam(required=false)LocalDate fechaHasta,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="createdAt")String sortBy,@RequestParam(defaultValue="DESC")String direction,@RequestParam(required=false)String columns)throws Exception{return excel("pedidos",all(n->api.orders(q,clienteId,patente,numero,estado,documento,fechaDesde,fechaHasta,includeDeleted,n,100,sortBy,direction).content()),columns);}

  private <T> List<T> all(Function<Integer,List<T>> get){List<T> r=new ArrayList<>();for(int p=0;;p++){List<T> part=get.apply(p);r.addAll(part);if(part.size()<100)return r;}}
  private ResponseEntity<byte[]> excel(String name,List<?> rows,String columns)throws Exception{List<String> fields=columns==null||columns.isBlank()?List.of():Arrays.stream(columns.split(",")).map(String::trim).toList();try(XSSFWorkbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){var sheet=wb.createSheet(name);var generated=sheet.createRow(0);generated.createCell(0).setCellValue("Generado: "+Instant.now());if(!rows.isEmpty()){var components=rows.getFirst().getClass().getRecordComponents();if(fields.isEmpty())fields=Arrays.stream(components).map(x->x.getName()).toList();var header=sheet.createRow(1);for(int c=0;c<fields.size();c++)header.createCell(c).setCellValue(fields.get(c));for(int r=0;r<rows.size();r++){var row=sheet.createRow(r+2);for(int c=0;c<fields.size();c++){String f=fields.get(c);for(var component:components)if(component.getName().equals(f)){Object val=component.getAccessor().invoke(rows.get(r));row.createCell(c).setCellValue(val==null?"":String.valueOf(val));break;}}}for(int c=0;c<fields.size();c++)sheet.autoSizeColumn(c);}wb.write(out);return attachment(name+".xlsx",MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),out.toByteArray());}}
  private byte[] pdf(OrderResponse o)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();Document d=new Document();PdfWriter.getInstance(d,out);d.open();d.add(new Paragraph("AVIANTO - "+o.documento()));d.add(new Paragraph("Pedido: "+o.numero()+"    Estado: "+o.estado()));d.add(new Paragraph("Cliente: "+o.cliente()+"    Moto: "+o.moto()+" ("+o.patente()+")"));d.add(new Paragraph("Vencimiento: "+o.vencimiento()));d.add(new Paragraph(" "));for(OrderItemResponse i:o.items())d.add(new Paragraph(i.descripcion()+" x"+i.cantidad()+"  $"+i.subtotal()));d.add(new Paragraph("TOTAL: $"+o.total()));d.close();return out.toByteArray();}
  private ResponseEntity<byte[]> attachment(String name,MediaType type,byte[] body){return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(body);}
}
