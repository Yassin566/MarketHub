package com.markethub.vendor.admin.api;

import com.markethub.identity.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AdminVendorAuthorizationTest {
    @Test
    void vendorAdminControllerRequiresAdministrativeRole() {
        PreAuthorize rule = AdminVendorController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(rule);
        assertEquals("hasAnyRole('ADMIN','SUPER_ADMIN')", rule.value());
    }

    @Test
    void rejectsUnsupportedVendorStatusBeforeDatabaseAccess() {
        AdminVendorService service = new AdminVendorService(
                mock(JdbcTemplate.class), mock(AppUserRepository.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.vendors("PENDING", null, 20, 0));
    }

    @Test
    void rejectsInvalidPaginationBeforeDatabaseAccess() {
        AdminVendorService service = new AdminVendorService(
                mock(JdbcTemplate.class), mock(AppUserRepository.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.vendors("APPROVED", null, 101, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.vendors("APPROVED", null, 20, -1));
    }
}
