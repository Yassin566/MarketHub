package com.markethub.vendor.admin.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminPaymentService {
    private static final int MAX_LIMIT = 100;
    private static final int MAX_EVENT_HISTORY = 100;
    private static final Set<String> PAYMENT_STATUSES = Set.of(
            "REQUESTED", "PROCESSING", "SUCCEEDED", "FAILED",
            "CANCELLED", "EXPIRED", "REFUNDED", "REQUIRES_REVIEW");

    private final JdbcTemplate jdbc;

    public AdminPaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PaymentView(
            UUID paymentId, UUID orderId, UUID customerUserId,
            String providerCode, String providerReference, String status,
            BigDecimal amount, String currency, Instant createdAt, Instant updatedAt) {}

    public record PaymentPage(List<PaymentView> items, long total, int limit, int offset) {}

    public record ProviderEventView(
            String eventId, String eventType, String outcome,
            Instant receivedAt, Instant processedAt) {}

    public record ProviderEventHistory(UUID paymentId, List<ProviderEventView> events) {}

    @Transactional(readOnly = true)
    public PaymentPage list(String requestedStatus, int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Payment page limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Payment page offset cannot be negative");
        }
        String status = normalizeStatus(requestedStatus);
        long total = status == null
                ? jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)
                : jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = ?", Long.class, status);

        String sql = """
                SELECT id, order_id, customer_user_id, provider_code, provider_reference,
                       status, amount, currency, created_at, updated_at
                FROM payments
                """ + (status == null ? "" : " WHERE status = ? ")
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";

        List<PaymentView> items = status == null
                ? jdbc.query(sql, (rs, row) -> payment(rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class), rs.getObject("customer_user_id", UUID.class),
                        rs.getString("provider_code"), rs.getString("provider_reference"),
                        rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")), limit, offset)
                : jdbc.query(sql, (rs, row) -> payment(rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class), rs.getObject("customer_user_id", UUID.class),
                        rs.getString("provider_code"), rs.getString("provider_reference"),
                        rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")), status, limit, offset);
        return new PaymentPage(items, total, limit, offset);
    }

    @Transactional(readOnly = true)
    public ProviderEventHistory events(UUID paymentId) {
        Long paymentCount = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE id = ?", Long.class, paymentId);
        if (paymentCount == null || paymentCount == 0) {
            throw new ResourceNotFoundException("Payment was not found");
        }
        List<ProviderEventView> events = jdbc.query("""
                SELECT event_id, event_type, outcome, received_at, processed_at
                FROM payment_provider_events
                WHERE payment_id = ?
                ORDER BY received_at DESC, id DESC
                LIMIT ?
                """, (rs, row) -> new ProviderEventView(
                rs.getString("event_id"), rs.getString("event_type"), rs.getString("outcome"),
                instant(rs.getTimestamp("received_at")), instant(rs.getTimestamp("processed_at"))),
                paymentId, MAX_EVENT_HISTORY);
        return new ProviderEventHistory(paymentId, events);
    }

    private static String normalizeStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return null;
        }
        String status = requestedStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (!PAYMENT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported payment status filter");
        }
        return status;
    }

    private static PaymentView payment(
            UUID id, UUID orderId, UUID customerId, String provider, String reference,
            String status, BigDecimal amount, String currency, Timestamp created, Timestamp updated) {
        return new PaymentView(id, orderId, customerId, provider, reference, status,
                amount, currency, instant(created), instant(updated));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
