package com.markethub.identity.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="app_users")
public class AppUser {
 @Id private UUID id;
 @Column(nullable=false,unique=true,length=254) private String email;
 @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private UserRole role;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private UserStatus status;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected AppUser(){}
 public static AppUser register(String email,String hash){AppUser u=new AppUser();u.id=UUID.randomUUID();u.email=email.trim().toLowerCase(java.util.Locale.ROOT);u.passwordHash=hash;u.role=UserRole.CUSTOMER;u.status=UserStatus.ACTIVE;u.createdAt=Instant.now();u.updatedAt=u.createdAt;return u;}
 public UUID getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public UserRole getRole(){return role;} public UserStatus getStatus(){return status;}
}