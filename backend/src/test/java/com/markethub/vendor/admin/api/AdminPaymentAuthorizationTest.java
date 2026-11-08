package com.markethub.vendor.admin.api;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminPaymentAuthorizationTest {
    @Test
    void paymentOperationsRequireAdministrativeRole() {
        PreAuthorize rule = AdminPaymentController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(rule);
        assertEquals("hasAnyRole('ADMIN','SUPER_ADMIN')", rule.value());
    }
}
