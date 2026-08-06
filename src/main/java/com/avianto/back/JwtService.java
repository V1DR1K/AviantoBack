package com.avianto.back;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class JwtService {
  private final SecretKey key; private final long accessMinutes; private final long refreshDays;
  public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.access-minutes}") long accessMinutes,@Value("${app.jwt.refresh-days}") long refreshDays){if(secret.length()<32)throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 caracteres");this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.accessMinutes=accessMinutes;this.refreshDays=refreshDays;}
  String access(AppUser user){return Jwts.builder().subject(user.id.toString()).claim("role",user.rol.name()).issuedAt(new Date()).expiration(Date.from(Instant.now().plus(Duration.ofMinutes(accessMinutes)))).signWith(key).compact();}
  String refresh(){return UUID.randomUUID()+"."+UUID.randomUUID();}
  Claims claims(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
  Instant refreshExpiry(){return Instant.now().plus(Duration.ofDays(refreshDays));}
}
