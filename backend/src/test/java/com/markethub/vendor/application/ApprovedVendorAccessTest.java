package com.markethub.vendor.application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ApprovedVendorAccessTest {
 @Mock JdbcTemplate jdbc;
 @InjectMocks ApprovedVendorAccess access;
 @Test void pendingOrRejectedVendorCannotUseVendorApis(){
  UUID userId=UUID.randomUUID();
  when(jdbc.query(anyString(),ArgumentMatchers.<RowMapper<UUID>>any(),eq(userId))).thenReturn(List.of());
  assertThrows(AccessDeniedException.class,()->access.requireApprovedVendor(userId));
 }
}