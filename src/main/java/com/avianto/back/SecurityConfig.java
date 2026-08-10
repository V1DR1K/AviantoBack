package com.avianto.back;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.*;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.util.*;

@Configuration @EnableMethodSecurity
class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
  @Bean SecurityFilterChain security(HttpSecurity http,JwtFilter filter)throws Exception{return http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/auth/refresh","/api/auth/logout","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**","/actuator/health").permitAll().requestMatchers(HttpMethod.GET,"/api/configuracion/trabajos/autocomplete").authenticated().requestMatchers("/api/configuracion/**").hasAuthority("ROLE_ADMINISTRACION").anyRequest().authenticated()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();}
  @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins){CorsConfiguration config=new CorsConfiguration();config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(value->!value.isEmpty()).toList());config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));config.setAllowedHeaders(List.of("Authorization","Content-Type"));config.setExposedHeaders(List.of("Content-Disposition"));UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;}
}
@Component class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt; JwtFilter(JwtService jwt){this.jwt=jwt;}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {String header=request.getHeader("Authorization");if(header!=null&&header.startsWith("Bearer "))try{Claims c=jwt.claims(header.substring(7));String role=c.get("role",String.class);var auth=new UsernamePasswordAuthenticationToken(c.getSubject(),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));SecurityContextHolder.getContext().setAuthentication(auth);}catch(Exception ignored){}chain.doFilter(request,response);}
}
