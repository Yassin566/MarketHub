package com.markethub.vendor.admin.api;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminPaymentResolutionServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminPaymentResolutionService service = new AdminPaymentResolutionService(jdbc);
    private final UUID paymentId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void rejectsMissingOrUnsupportedDecisionBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId,
                        new AdminPaymentResolutionService.ResolutionRequest(null, "evidence", "reason")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId,
                        new AdminPaymentResolutionService.ResolutionRequest("REFUND", "evidence", "reason")));
        verifyNoInteractions(jdbc);
    }

    @Test
    void requiresNonBlankEvidenceAndReasonBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId,
                        new AdminPaymentResolutionService.ResolutionRequest("CONFIRM_FAILED", " ", "reason")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId,
                        new AdminPaymentResolutionService.ResolutionRequest("CONFIRM_FAILED", "evidence", "\t ")));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsEvidenceAndReasonLongerThanDatabaseLimits() {
        String evidence = "e".repeat(301);
        String reason = "r".repeat(501);
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId,
                        new AdminPaymentResolutionService.ResolutionRequest("CONFIRM_SUCCEEDED", evidence, "reason")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(paymentId, actorId,
                        new AdminPaymentResolutionService.ResolutionRequest("CONFIRM_SUCCEEDED", "evidence", reason)));
        verifyNoInteractions(jdbc);
    }
}
