package com.markethub.identity.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
@Service
public class JwtService {
 private final SecretKey key; private final String issuer; private final long ttl;
 public JwtService(@Value("${security.jwt.secret}") String secret,@Value("${security.jwt.issuer:markethub}") String issuer,@Value("${security.jwt.access-ttl-seconds:900}") long ttl){
  byte[] b=secret.getBytes(StandardCharsets.UTF_8);if(b.length<32)throw new IllegalStateException("JWT secret must be at least 32 UTF-8 bytes");key=Keys.hmacShaKeyFor(b);this.issuer=issuer;this.ttl=ttl;
 }
 public String issueAccessToken(MarketHubPrincipal p){Instant now=Instant.now();return Jwts.builder().issuer(issuer).subject(p.userId().toString()).claim("email",p.getUsername()).claim("role",p.role().name()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl))).signWith(key).compact();}
 public UUID subject(String t){return UUID.fromString(claims(t).getSubject());}
 public String email(String t){return claims(t).get("email",String.class);}
 private Claims claims(String t){return Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(t).getPayload();}
 public long accessTtlSeconds(){return ttl;}
}