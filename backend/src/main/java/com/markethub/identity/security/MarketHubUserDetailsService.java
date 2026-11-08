package com.markethub.identity.security;
import com.markethub.identity.repository.AppUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service
public class MarketHubUserDetailsService implements UserDetailsService {
 private final AppUserRepository users;
 public MarketHubUserDetailsService(AppUserRepository users){this.users=users;}
 public UserDetails loadUserByUsername(String email){return users.findByEmailIgnoreCase(email).map(MarketHubPrincipal::from).orElseThrow(()->new UsernameNotFoundException("User not found"));}
}