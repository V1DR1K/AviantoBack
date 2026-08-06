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
  @Override @Transactional public void run(ApplicationArguments args){if(adminUsername.isBlank()||adminName.isBlank()||adminPassword.isBlank()||operatorUsername.isBlank()||operatorName.isBlank()||operatorPassword.isBlank())throw new IllegalStateException("SEED_ADMIN_* y SEED_OPERATOR_* son obligatorias");if(db.one("select u from AppUser u where lower(u.username)=:username",AppUser.class,Map.of("username",adminUsername.trim().toLowerCase()))==null)create(adminUsername,adminName,adminPassword,Role.ADMINISTRACION);if(db.one("select u from AppUser u where lower(u.username)=:username",AppUser.class,Map.of("username",operatorUsername.trim().toLowerCase()))==null)create(operatorUsername,operatorName,operatorPassword,Role.OPERARIO);}
  private void create(String username,String name,String password,Role role){AppUser u=new AppUser();u.username=username.trim().toLowerCase();u.nombre=name.trim();u.passwordHash=encoder.encode(password);u.rol=role;db.persist(u);}
}
