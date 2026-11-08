package com.markethub.vendor.catalog.api;
import com.markethub.vendor.application.ApprovedVendorAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
  VendorProductRequest request=new VendorProductRequest("New name","new-name","description",new java.math.BigDecimal("12.5000"),"USD");
  assertThrows(IllegalArgumentException.class,()->service.update(user,foreignProduct,request));
 }
 @Test void archiveCannotMutateProductOutsideResolvedVendor(){
  UUID user=UUID.randomUUID(),ownVendor=UUID.randomUUID(),foreignProduct=UUID.randomUUID();
  when(access.requireApprovedVendor(user)).thenReturn(ownVendor);
  when(jdbc.update(contains("set status='ARCHIVED'"),eq(foreignProduct),eq(ownVendor))).thenReturn(0);
  assertThrows(IllegalArgumentException.class,()->service.archive(user,foreignProduct));
 }
}