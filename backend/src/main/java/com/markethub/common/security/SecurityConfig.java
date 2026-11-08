package com.markethub.common.security;
import com.markethub.identity.security.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean DaoAuthenticationProvider authenticationProvider(MarketHubUserDetailsService users,PasswordEncoder encoder){DaoAuthenticationProvider p=new DaoAuthenticationProvider(users);p.setPasswordEncoder(encoder);return p;}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity h,JwtAuthenticationFilter f,DaoAuthenticationProvider p)throws Exception{
  return h.csrf(c->c.disable()).authenticationProvider(p).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/api/v1/auth/**").permitAll()
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/products/**","/api/v1/vendors/*/products").permitAll().anyRequest().authenticated())
   .addFilterBefore(f,UsernamePasswordAuthenticationFilter.class).httpBasic(b->b.disable()).formLogin(x->x.disable()).build();
 }
}