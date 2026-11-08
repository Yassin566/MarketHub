package com.markethub.identity.security;
import com.markethub.identity.domain.AppUser;
import com.markethub.identity.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;
public record MarketHubPrincipal(UUID userId,String username,String password,UserRole role,boolean enabled) implements UserDetails {
 public static MarketHubPrincipal from(AppUser u){return new MarketHubPrincipal(u.getId(),u.getEmail(),u.getPasswordHash(),u.getRole(),u.getStatus()==com.markethub.identity.domain.UserStatus.ACTIVE);}
 public Collection<? extends GrantedAuthority> getAuthorities(){return List.of(new SimpleGrantedAuthority("ROLE_"+role.name()));}
 public String getPassword(){return password;} public String getUsername(){return username;}
 public boolean isAccountNonExpired(){return true;} public boolean isAccountNonLocked(){return enabled;} public boolean isCredentialsNonExpired(){return true;} public boolean isEnabled(){return enabled;}
}