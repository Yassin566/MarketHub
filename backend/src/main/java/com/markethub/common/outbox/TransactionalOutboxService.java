package com.markethub.common.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionalOutboxService {
    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_LEASE_SECONDS = 3_600;

    private final JdbcTemplate jdbc;

    public TransactionalOutboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OutboxMessage(
            UUID id,
            String eventType,
            String payload,
            int attempts,
            UUID leaseToken) {}

    /**
     * Claims due events in a short transaction. SKIP LOCKED allows multiple workers
     * to claim disjoint batches; the lease token fences off workers whose lease expired.
     */
    @Transactional
    public List<OutboxMessage> claimBatch(int batchSize, Duration leaseDuration) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
        long leaseSeconds = requireSeconds(leaseDuration, MAX_LEASE_SECONDS, "leaseDuration");

        jdbc.update("""
                UPDATE transactional_outbox
                SET status = 'DEAD',
                    claimed_at = NULL,
                    lease_token = NULL,
                    last_error = COALESCE(last_error, 'Maximum delivery attempts exceeded'),
                    updated_at = now()
                WHERE attempts >= ?
                  AND (
                    (status = 'PENDING' AND available_at <= now())
                    OR (status = 'PROCESSING'
                        AND claimed_at < now() - (? * interval '1 second'))
                  )
                """, MAX_ATTEMPTS, leaseSeconds);

        return jdbc.query("""
                WITH candidates AS (
                    SELECT id
                    FROM transactional_outbox
                    WHERE attempts < ?
                      AND (
                        (status = 'PENDING' AND available_at <= now())
                        OR (status = 'PROCESSING'
                            AND claimed_at < now() - (? * interval '1 second'))
                      )
                    ORDER BY created_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE transactional_outbox AS event
                SET status = 'PROCESSING',
                    claimed_at = now(),
                    lease_token = gen_random_uuid(),
                    attempts = event.attempts + 1,
                    updated_at = now()
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING event.id, event.event_type, event.payload::text,
                          event.attempts, event.lease_token
                """, rs -> {
                    List<OutboxMessage> messages = new ArrayList<>();
                    while (rs.next()) {
                        messages.add(new OutboxMessage(
                                rs.getObject("id", UUID.class),
                                rs.getString("event_type"),
                                rs.getString("payload"),
                                rs.getInt("attempts"),
                                rs.getObject("lease_token", UUID.class)));
                    }
                    return List.copyOf(messages);
                }, MAX_ATTEMPTS, leaseSeconds, batchSize);
    }

    /**
     * Completes an event only when the caller still owns its current lease.
     */
    @Transactional
    public boolean markDelivered(UUID eventId, UUID leaseToken) {
        return jdbc.update("""
                UPDATE transactional_outbox
                SET status = 'DELIVERED',
                    delivered_at = now(),
                    claimed_at = NULL,
                    lease_token = NULL,
                    last_error = NULL,
                    updated_at = now()
                WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, eventId, leaseToken) == 1;
    }

    /**
     * Releases a failed event for retry with caller-selected backoff. Once the
     * maximum number of claims has been reached, the event is moved to DEAD.
     */
    @Transactional
    public boolean reschedule(UUID eventId, UUID leaseToken, String error, Duration delay) {
        long delaySeconds = requireSeconds(delay, 86_400, "delay");
        String safeError = error == null || error.isBlank()
                ? "Delivery failed without an error description"
                : error.substring(0, Math.min(error.length(), 2_000));

        return jdbc.update("""
                UPDATE transactional_outbox
                SET status = CASE WHEN attempts >= ? THEN 'DEAD' ELSE 'PENDING' END,
                    available_at = now() + (? * interval '1 second'),
                    claimed_at = NULL,
                    lease_token = NULL,
                    last_error = ?,
                    updated_at = now()
                WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, MAX_ATTEMPTS, delaySeconds, safeError, eventId, leaseToken) == 1;
    }

    private static long requireSeconds(Duration duration, long maximum, String name) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be zero or greater");
        }
        long seconds;
        try {
            seconds = duration.toSeconds();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
        if (seconds > maximum) {
            throw new IllegalArgumentException(name + " must not exceed " + maximum + " seconds");
        }
        return seconds;
    }
}
