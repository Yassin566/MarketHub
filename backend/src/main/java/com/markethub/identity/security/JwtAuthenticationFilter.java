package com.markethub.identity.security;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final MarketHubUserDetailsService users;
 public JwtAuthenticationFilter(JwtService jwt,MarketHubUserDetailsService users){this.jwt=jwt;this.users=users;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String h=req.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){
   try{UserDetails d=users.loadUserByUsername(jwt.email(h.substring(7)));if(d.isEnabled()){var a=new UsernamePasswordAuthenticationToken(d,null,d.getAuthorities());a.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));SecurityContextHolder.getContext().setAuthentication(a);}}
   catch(JwtException|IllegalArgumentException|org.springframework.security.core.userdetails.UsernameNotFoundException ignored){SecurityContextHolder.clearContext();}
  }
  chain.doFilter(req,res);
 }
}