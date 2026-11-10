package com.avianto.back;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.*;

@Component class SeedService implements ApplicationRunner {
  private final DataRepository db; private final PasswordEncoder encoder; private final String adminUsername,adminName,adminPassword,operatorUsername,operatorName,operatorPassword;
  SeedService(DataRepository db,PasswordEncoder encoder,@Value("${SEED_ADMIN_USERNAME:}")String adminUsername,@Value("${SEED_ADMIN_NAME:}")String adminName,@Value("${SEED_ADMIN_PASSWORD:}")String adminPassword,@Value("${SEED_OPERATOR_USERNAME:}")String operatorUsername,@Value("${SEED_OPERATOR_NAME:}")String operatorName,@Value("${SEED_OPERATOR_PASSWORD:}")String operatorPassword){this.db=db;this.encoder=encoder;this.adminUsername=adminUsername;this.adminName=adminName;this.adminPassword=adminPassword;this.operatorUsername=operatorUsername;this.operatorName=operatorName;this.operatorPassword=operatorPassword;}
  @Override @Transactional public void run(ApplicationArguments args){ensureAdministrationRole();if(!adminUsername.isBlank()&&!adminName.isBlank()&&!adminPassword.isBlank())ensureUser(adminUsername,adminName,adminPassword,Permisos.CODIGO_ADMINISTRACION);if(!operatorUsername.isBlank()&&!operatorName.isBlank()&&!operatorPassword.isBlank())ensureUser(operatorUsername,operatorName,operatorPassword,Permisos.CODIGO_OPERARIO);ensureReferenceData();}
  private void ensureUser(String username,String name,String password,String rolCodigo){if(db.one("select u from AppUser u where lower(u.username)=:username and u.deletedAt is null",AppUser.class,Map.of("username",username.trim().toLowerCase()))==null)create(username,name,password,rolCodigo);}
  private void create(String username,String name,String password,String rolCodigo){AppUser u=new AppUser();u.username=username.trim().toLowerCase();u.nombre=name.trim();u.passwordHash=encoder.encode(password);u.rol=rolPorCodigo(rolCodigo);db.persist(u);}
  private Rol rolPorCodigo(String codigo){return db.one("select r from Rol r where r.codigo=:codigo and r.deletedAt is null",Rol.class,Map.of("codigo",codigo));}
  // El rol Administración siempre tiene todo el catálogo; si se agregan permisos nuevos en el código, se incorporan al arrancar.
  private void ensureAdministrationRole(){Rol admin=rolPorCodigo(Permisos.CODIGO_ADMINISTRACION);if(admin==null)throw new IllegalStateException("Falta el rol de sistema Administración (migración V35)");admin.permisos.retainAll(Permisos.TODOS);admin.permisos.addAll(Permisos.TODOS);}
  private void ensureReferenceData(){for(String name:new String[]{"Honda","Yamaha","Bajaj","Gilera","Corven","Zanella","Mondial","Guerrero"})ensureBrand(name);for(String name:new String[]{"General","Frenos","Ruedas y neumaticos","Aceite y liquidos","Electrico y luces","Documentacion"})ensureCategory(name);for(String name:new String[]{"Revisar luces","Frenos delanteros","Frenos traseros","Presion de neumaticos","Pérdidas de liquidos","Confirmar trabajos realizados","Verificar limpieza","Confirmar documentacion","Espejos y accesorios","Bateria y arranque"})ensureControl(name);}
  private void ensureBrand(String name){if(db.one("select e from MarcaMoto e where lower(e.nombre)=:name and e.deletedAt is null",MarcaMoto.class,Map.of("name",name.toLowerCase()))==null){MarcaMoto e=new MarcaMoto();e.nombre=name;db.persist(e);}}
  private void ensureCategory(String name){if(db.one("select e from Categoria e where lower(e.nombre)=:name and e.deletedAt is null",Categoria.class,Map.of("name",name.toLowerCase()))==null){Categoria e=new Categoria();e.nombre=name;db.persist(e);}}
  private void ensureControl(String name){if(db.one("select e from ControlRevision e where lower(e.nombre)=:name and e.deletedAt is null",ControlRevision.class,Map.of("name",name.toLowerCase()))==null){ControlRevision e=new ControlRevision();e.nombre=name;e.orden=(int)db.count("select count(c) from ControlRevision c where c.deletedAt is null",Map.of())+1;db.persist(e);}}
}
