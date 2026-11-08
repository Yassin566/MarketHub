package com.markethub.vendor.api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class VendorApplicationService {
 private final JdbcTemplate jdbc;
 public VendorApplicationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional public VendorApplicationController.VendorApplicationResult apply(UUID userId,VendorApplicationRequest r){
  Integer n=jdbc.queryForObject("select count(*) from vendors where owner_user_id = ?",Integer.class,userId);
  if(n!=null&&n>0)throw new IllegalArgumentException("A vendor application already exists for this account");
  UUID id=UUID.randomUUID();String name=r.displayName().trim();
  jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'PENDING')",id,userId,name);
  return new VendorApplicationController.VendorApplicationResult(id,name,"PENDING");
 }
}