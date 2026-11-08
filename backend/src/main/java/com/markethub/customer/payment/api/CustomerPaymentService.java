package com.markethub.customer.payment.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerPaymentService {
    private final JdbcTemplate jdbc;

    public CustomerPaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PaymentView(UUID id, UUID orderId, String status, String providerCode,
                              BigDecimal amount, String currency, Instant createdAt) {}

    @Transactional
    public PaymentView requestPayment(UUID customerId, UUID orderId, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new IllegalArgumentException("Idempotency-Key is required");

        PaymentView previous = findByIdempotencyKey(customerId, idempotencyKey);
        if (previous != null) {
            if (!previous.orderId().equals(orderId)) {
                throw new IllegalStateException("Idempotency-Key was already used for another order");
            }
            return previous;
        }

        OrderPaymentSnapshot order = loadOrderForUpdate(customerId, orderId);
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new IllegalStateException("Order is not awaiting payment");
        }

        // Lock reservation rows so expiry cannot release stock between validation and commit.
        List<UUID> reservationIds = jdbc.query("""
                SELECT id FROM inventory_reservations
                WHERE order_id = ?
                ORDER BY product_id
                FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), orderId);
        Long activeReservations = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_reservations
                WHERE order_id = ? AND status = 'ACTIVE' AND expires_at > now()
                """, Long.class, orderId);
        Long invalidReservations = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_reservations
                WHERE order_id = ? AND (status <> 'ACTIVE' OR expires_at <= now())
                """, Long.class, orderId);
        if (reservationIds.isEmpty() || activeReservations == null || activeReservations == 0
                || invalidReservations == null || invalidReservations > 0) {
            throw new IllegalStateException("Order inventory reservations are no longer valid");
        }

        Integer activePayment = jdbc.query("""
                SELECT 1 FROM payments
                WHERE order_id = ? AND status IN ('REQUESTED','PROCESSING')
                LIMIT 1
                """, rs -> rs.next() ? 1 : null, orderId);
        if (activePayment != null) {
            throw new IllegalStateException("A payment attempt is already active for this order");
        }

        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payments
                    (id, order_id, customer_user_id, idempotency_key, provider_code,
                     status, amount, currency)
                VALUES (?, ?, ?, ?, 'UNCONFIGURED', 'REQUESTED', ?, ?)
                """, paymentId, orderId, customerId, idempotencyKey, order.total(), order.currency());

        String payload = """
                {"paymentId":"%s","orderId":"%s","amount":"%s","currency":"%s"}
                """.formatted(paymentId, orderId, order.total().toPlainString(), order.currency()).trim();
        jdbc.update("""
                INSERT INTO transactional_outbox
                    (id, event_key, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, 'PAYMENT', ?, 'PAYMENT_REQUESTED', ?::jsonb)
                """, UUID.randomUUID(), paymentId, paymentId, payload);

        return loadPayment(customerId, paymentId);
    }

    private OrderPaymentSnapshot loadOrderForUpdate(UUID customerId, UUID orderId) {
        List<OrderPaymentSnapshot> orders = jdbc.query("""
                SELECT id, status, total, currency
                FROM customer_orders
                WHERE id = ? AND customer_user_id = ?
                FOR UPDATE
                """, (rs, row) -> new OrderPaymentSnapshot(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getBigDecimal("total"), rs.getString("currency")), orderId, customerId);
        if (orders.isEmpty()) throw new ResourceNotFoundException("Order not found");
        return orders.getFirst();
    }

    private PaymentView findByIdempotencyKey(UUID customerId, UUID key) {
        List<PaymentView> payments = jdbc.query("""
                SELECT id, order_id, status, provider_code, amount, currency, created_at
                FROM payments WHERE customer_user_id = ? AND idempotency_key = ?
                """, PAYMENT_MAPPER, customerId, key);
        return payments.isEmpty() ? null : payments.getFirst();
    }

    private PaymentView loadPayment(UUID customerId, UUID paymentId) {
        List<PaymentView> payments = jdbc.query("""
                SELECT id, order_id, status, provider_code, amount, currency, created_at
                FROM payments WHERE customer_user_id = ? AND id = ?
                """, PAYMENT_MAPPER, customerId, paymentId);
        if (payments.isEmpty()) throw new ResourceNotFoundException("Payment not found");
        return payments.getFirst();
    }

    private record OrderPaymentSnapshot(UUID id, String status, BigDecimal total, String currency) {}

    private static final org.springframework.jdbc.core.RowMapper<PaymentView> PAYMENT_MAPPER =
            (rs, row) -> new PaymentView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("order_id", UUID.class),
                    rs.getString("status"),
                    rs.getString("provider_code"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency"),
                    rs.getTimestamp("created_at").toInstant());
}
