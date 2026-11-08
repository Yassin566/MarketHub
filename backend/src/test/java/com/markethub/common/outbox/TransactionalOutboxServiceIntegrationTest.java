package com.markethub.common.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TransactionalOutboxServiceIntegrationTest {
    @Autowired private TransactionalOutboxService outbox;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void claimsDueEventAndRequiresCurrentLeaseToComplete() {
        UUID eventId = insertEvent();

        var claimed = outbox.claimBatch(10, Duration.ofMinutes(2));

        assertThat(claimed).hasSize(1);
        var message = claimed.getFirst();
        assertThat(message.id()).isEqualTo(eventId);
        assertThat(message.eventType()).isEqualTo("PAYMENT_REQUESTED");
        assertThat(message.payload()).contains("\"paymentId\"");
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.leaseToken()).isNotNull();

        assertThat(outbox.markDelivered(eventId, UUID.randomUUID())).isFalse();
        assertThat(outbox.markDelivered(eventId, message.leaseToken())).isTrue();
        assertThat(outbox.markDelivered(eventId, message.leaseToken())).isFalse();

        assertThat(statusOf(eventId)).isEqualTo("DELIVERED");
    }

    @Test
    void failedDeliveryIsRescheduledAndNotClaimedBeforeItsBackoffExpires() {
        UUID eventId = insertEvent();

        var first = outbox.claimBatch(1, Duration.ofMinutes(2)).getFirst();
        assertThat(outbox.reschedule(eventId, first.leaseToken(), "temporary provider outage",
                Duration.ofHours(1))).isTrue();

        assertThat(outbox.claimBatch(1, Duration.ofMinutes(2))).isEmpty();
        assertThat(statusOf(eventId)).isEqualTo("PENDING");
        assertThat(lastErrorOf(eventId)).isEqualTo("temporary provider outage");
    }

    @Test
    void reclaimsExpiredLeaseAndFencesOffPreviousWorker() {
        UUID eventId = insertEvent();
        var first = outbox.claimBatch(1, Duration.ofSeconds(1)).getFirst();

        jdbc.update("""
                UPDATE transactional_outbox
                SET claimed_at = now() - interval '10 minutes'
                WHERE id = ?
                """, eventId);

        var second = outbox.claimBatch(1, Duration.ofSeconds(30)).getFirst();

        assertThat(second.id()).isEqualTo(eventId);
        assertThat(second.leaseToken()).isNotEqualTo(first.leaseToken());
        assertThat(second.attempts()).isEqualTo(2);
        assertThat(outbox.markDelivered(eventId, first.leaseToken())).isFalse();
        assertThat(outbox.markDelivered(eventId, second.leaseToken())).isTrue();
    }

    @Test
    void validatesClaimBounds() {
        assertThatThrownBy(() -> outbox.claimBatch(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.claimBatch(1, Duration.ofDays(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID insertEvent() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO transactional_outbox
                    (id, event_key, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, 'PAYMENT', ?, 'PAYMENT_REQUESTED',
                        '{"paymentId":"test-payment","orderId":"test-order"}'::jsonb)
                """, id, UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    private String statusOf(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT status FROM transactional_outbox WHERE id = ?", String.class, eventId);
    }

    private String lastErrorOf(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT last_error FROM transactional_outbox WHERE id = ?", String.class, eventId);
    }
}
