package com.markethub.vendor.analytics.api;

import com.markethub.vendor.application.ApprovedVendorAccess;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VendorAnalyticsServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApprovedVendorAccess access = mock(ApprovedVendorAccess.class);
    private final VendorAnalyticsService service = new VendorAnalyticsService(jdbc, access);

    @Test
    void rejectsReversedDateRangeBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () ->
            service.report(UUID.randomUUID(), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1)));
        verifyNoInteractions(jdbc, access);
    }

    @Test
    void rejectsRangesLongerThan366DaysBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () ->
            service.report(UUID.randomUUID(), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)));
        verifyNoInteractions(jdbc, access);
    }

    @Test
    void rejectsMissingDateBoundsBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () ->
            service.report(UUID.randomUUID(), null, LocalDate.now()));
        verifyNoInteractions(jdbc, access);
    }
}
