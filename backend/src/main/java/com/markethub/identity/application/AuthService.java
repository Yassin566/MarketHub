package com.markethub.identity.application;
import com.markethub.identity.api.*;
import com.markethub.identity.domain.AppUser;
import com.markethub.identity.repository.AppUserRepository;
import com.markethub.identity.security.*;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
 private final AppUserRepository users; private final PasswordEncoder encoder; private final AuthenticationManager manager; private final JwtService jwt;
 public AuthService(AppUserRepository users,PasswordEncoder encoder,AuthenticationManager manager,JwtService jwt){this.users=users;this.encoder=encoder;this.manager=manager;this.jwt=jwt;}
 @Transactional public AuthResponse register(RegisterRequest r){
  String email=r.email().trim().toLowerCase(java.util.Locale.ROOT);
  if(users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("An account with this email already exists");
  return response(MarketHubPrincipal.from(users.save(AppUser.register(email,encoder.encode(r.password())))));
 }
 public AuthResponse login(LoginRequest r){
  var a=manager.authenticate(new UsernamePasswordAuthenticationToken(r.email().trim(),r.password()));
  return response((MarketHubPrincipal)a.getPrincipal());
 }
 private AuthResponse response(MarketHubPrincipal p){return new AuthResponse(jwt.issueAccessToken(p),"Bearer",jwt.accessTtlSeconds(),p.userId(),p.getUsername(),p.role().name());}
}