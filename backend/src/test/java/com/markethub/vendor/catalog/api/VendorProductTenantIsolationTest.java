package com.markethub.vendor.catalog.api;
import com.markethub.vendor.application.ApprovedVendorAccess;
import com.markethub.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class VendorProductTenantIsolationTest {
 @Mock JdbcTemplate jdbc;
 @Mock ApprovedVendorAccess access;
 @InjectMocks VendorProductService service;
 @Test void updateCannotFindProductOwnedByAnotherVendor(){
  UUID user=UUID.randomUUID(),ownVendor=UUID.randomUUID(),foreignProduct=UUID.randomUUID();
  when(access.requireApprovedVendor(user)).thenReturn(ownVendor);
  when(jdbc.query(anyString(),ArgumentMatchers.<RowMapper<VendorProductService.ProductView>>any(),eq(foreignProduct),eq(ownVendor))).thenReturn(List.of());
  VendorProductRequest request=new VendorProductRequest("New name","new-name","description",new BigDecimal("12.5000"),"USD",0L);
  assertThrows(ResourceNotFoundException.class,()->service.update(user,foreignProduct,request));
 }
 @Test void staleClientVersionCannotOverwriteNewerProduct(){
  UUID user=UUID.randomUUID(),vendor=UUID.randomUUID(),product=UUID.randomUUID();
  when(access.requireApprovedVendor(user)).thenReturn(vendor);
  var current=new VendorProductService.ProductView(product,"Current","current","description",new BigDecimal("12.5000"),"USD","ACTIVE",2L,Instant.now(),Instant.now());
  when(jdbc.query(anyString(),ArgumentMatchers.<RowMapper<VendorProductService.ProductView>>any(),eq(product),eq(vendor))).thenReturn(List.of(current));
  var staleRequest=new VendorProductRequest("Stale write","stale-write","description",new BigDecimal("14.0000"),"USD",1L);
  assertThrows(OptimisticLockingFailureException.class,()->service.update(user,product,staleRequest));
  verify(jdbc,never()).update(anyString(),any(),any(),any(),any(),any(),any(),any(),any());
 }
 @Test void archiveCannotMutateProductOutsideResolvedVendor(){
  UUID user=UUID.randomUUID(),ownVendor=UUID.randomUUID(),foreignProduct=UUID.randomUUID();
  when(access.requireApprovedVendor(user)).thenReturn(ownVendor);
  when(jdbc.update(contains("set status='ARCHIVED'"),eq(foreignProduct),eq(ownVendor))).thenReturn(0);
  assertThrows(ResourceNotFoundException.class,()->service.archive(user,foreignProduct));
 }
}
