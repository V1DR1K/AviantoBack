package com.avianto.back;

import static com.avianto.back.ApiDtos.*;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @Transactional
public class AuthService {
  private final DataRepository db; private final JwtService jwt; private final PasswordEncoder encoder; private final ApiService api;
  AuthService(DataRepository db,JwtService jwt,PasswordEncoder encoder,ApiService api){this.db=db;this.jwt=jwt;this.encoder=encoder;this.api=api;}
  public SessionResponse login(LoginRequest r){AppUser u=db.one("select u from AppUser u where lower(u.username)=:username and u.deletedAt is null",AppUser.class,Map.of("username",r.username().trim().toLowerCase()));if(u==null||!u.activo||!encoder.matches(r.password(),u.passwordHash))throw new BusinessException(401,"Credenciales inválidas");return session(u);}
  public SessionResponse refresh(RefreshRequest r){RefreshToken t=db.one("select t from RefreshToken t where t.tokenHash=:hash",RefreshToken.class,Map.of("hash",hash(r.refreshToken())));if(t==null||t.revokedAt!=null||t.expiresAt.isBefore(Instant.now())||!t.user.activo||t.user.deletedAt!=null)throw new BusinessException(401,"Sesión inválida");t.revokedAt=Instant.now();return session(t.user);}
  public void logout(RefreshRequest r){RefreshToken t=db.one("select t from RefreshToken t where t.tokenHash=:hash",RefreshToken.class,Map.of("hash",hash(r.refreshToken())));if(t!=null)t.revokedAt=Instant.now();}
  public UserResponse me(){return api.user(api.actor());}
  private SessionResponse session(AppUser u){String raw=jwt.refresh();RefreshToken t=new RefreshToken();t.user=u;t.tokenHash=hash(raw);t.expiresAt=jwt.refreshExpiry();db.persist(t);return new SessionResponse(jwt.access(u),raw,api.user(u));}
  private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
