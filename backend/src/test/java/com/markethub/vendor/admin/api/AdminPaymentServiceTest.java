package com.markethub.vendor.admin.api;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AdminPaymentServiceTest {
    private final AdminPaymentService service = new AdminPaymentService(mock(JdbcTemplate.class));

    @Test
    void rejectsUnknownPaymentStatusBeforeQueryingDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.list("PAID", 50, 0));
    }

    @Test
    void rejectsPageLimitOutsideBoundedRange() {
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 0, 0));
    }

    @Test
    void rejectsNegativePageOffset() {
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 50, -1));
    }
}
