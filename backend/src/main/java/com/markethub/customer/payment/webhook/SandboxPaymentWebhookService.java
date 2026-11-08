package com.markethub.customer.payment.webhook;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SandboxPaymentWebhookService {
    private static final String PROVIDER_CODE = "SANDBOX";
    private final JdbcTemplate jdbc;

    public SandboxPaymentWebhookService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record WebhookResult(boolean duplicate, String outcome) {}
    private record PaymentState(UUID id, UUID orderId, String status, BigDecimal amount, String currency) {}
    private record Reservation(UUID id, UUID productId, long quantity, String status, boolean unexpired) {}

    @Transactional
    public WebhookResult process(
            String eventId, String eventType, String providerReference,
            String amountText, String currency, String rawBody) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        if (amount.signum() < 0 || currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid payment amount or currency");
        }
        String payloadHash = sha256(rawBody);

        List<String> priorHashes = jdbc.query("""
                SELECT payload_sha256 FROM payment_provider_events
                WHERE provider_code = ? AND event_id = ?
                """, (rs, row) -> rs.getString("payload_sha256"), PROVIDER_CODE, eventId);
        if (!priorHashes.isEmpty()) {
            if (!sameHash(priorHashes.getFirst(), payloadHash)) {
                throw new IllegalStateException("Webhook event ID was reused with a different payload");
            }
            return new WebhookResult(true, "DUPLICATE");
        }

        List<PaymentState> matches = jdbc.query("""
                SELECT id, order_id, status, amount, currency
                FROM payments
                WHERE provider_code = ? AND provider_reference = ?
                FOR UPDATE
                """, (rs, row) -> new PaymentState(
                rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("currency")),
                PROVIDER_CODE, providerReference);
        if (matches.isEmpty()) {
            throw new IllegalStateException("Payment reference was not found");
        }
        PaymentState payment = matches.getFirst();
        if (payment.amount().compareTo(amount) != 0 || !payment.currency().equals(currency)) {
            throw new IllegalStateException("Webhook amount or currency does not match the payment");
        }

        int eventInserted = jdbc.update("""
                INSERT INTO payment_provider_events
                    (id, provider_code, event_id, payment_id, event_type, payload_sha256)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider_code, event_id) DO NOTHING
                """, UUID.randomUUID(), PROVIDER_CODE, eventId, payment.id(), eventType, payloadHash);
        if (eventInserted == 0) {
            List<String> concurrentHashes = jdbc.query("""
                    SELECT payload_sha256 FROM payment_provider_events
                    WHERE provider_code = ? AND event_id = ?
                    """, (rs, row) -> rs.getString("payload_sha256"), PROVIDER_CODE, eventId);
            if (concurrentHashes.isEmpty() || !sameHash(concurrentHashes.getFirst(), payloadHash)) {
                throw new IllegalStateException("Webhook event ID was reused with a different payload");
            }
            return new WebhookResult(true, "DUPLICATE");
        }

        if (!"PROCESSING".equals(payment.status())) {
            String outcome = "IGNORED_" + payment.status();
            markProcessed(eventId, outcome);
            return new WebhookResult(false, outcome);
        }

        List<Reservation> reservations = jdbc.query("""
                SELECT id, product_id, quantity, status, expires_at > now() AS unexpired
                FROM inventory_reservations
                WHERE order_id = ?
                ORDER BY product_id
                FOR UPDATE
                """, (rs, row) -> new Reservation(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getLong("quantity"), rs.getString("status"), rs.getBoolean("unexpired")),
                payment.orderId());

        // Keep the reservation -> inventory -> order lock order used by expiry processing.
        if ("payment.succeeded".equals(eventType)) {
            if (reservations.isEmpty() || reservations.stream()
                    .anyMatch(r -> !"ACTIVE".equals(r.status()) || !r.unexpired())) {
                jdbc.update("""
                        UPDATE payments SET status = 'REQUIRES_REVIEW', updated_at = now()
                        WHERE id = ? AND status = 'PROCESSING'
                        """, payment.id());
                markProcessed(eventId, "REQUIRES_REVIEW_RESERVATION_EXPIRED");
                return new WebhookResult(false, "REQUIRES_REVIEW");
            }
            for (Reservation reservation : reservations) {
                int changed = jdbc.update("""
                        UPDATE product_inventory
                        SET on_hand_quantity = on_hand_quantity - ?,
                            reserved_quantity = reserved_quantity - ?,
                            version = version + 1, updated_at = now()
                        WHERE product_id = ? AND on_hand_quantity >= ?
                          AND reserved_quantity >= ?
                        """, reservation.quantity(), reservation.quantity(),
                        reservation.productId(), reservation.quantity(), reservation.quantity());
                if (changed != 1) {
                    throw new IllegalStateException("Reserved inventory is inconsistent");
                }
            }
            int orderChanged = jdbc.update("""
                    UPDATE customer_orders SET status = 'PAID', updated_at = now()
                    WHERE id = ? AND status = 'PENDING_PAYMENT'
                    """, payment.orderId());
            if (orderChanged != 1) {
                throw new IllegalStateException("Order is no longer eligible for payment success");
            }
            jdbc.update("""
                    UPDATE vendor_orders SET status = 'PAID', updated_at = now()
                    WHERE order_id = ? AND status = 'PENDING_PAYMENT'
                    """, payment.orderId());
            jdbc.update("""
                    UPDATE inventory_reservations SET status = 'CONSUMED', updated_at = now()
                    WHERE order_id = ? AND status = 'ACTIVE'
                    """, payment.orderId());
            jdbc.update("""
                    UPDATE payments SET status = 'SUCCEEDED', updated_at = now()
                    WHERE id = ? AND status = 'PROCESSING'
                    """, payment.id());
            markProcessed(eventId, "SUCCEEDED");
            return new WebhookResult(false, "SUCCEEDED");
        }

        if ("payment.failed".equals(eventType)) {
            for (Reservation reservation : reservations) {
                if ("ACTIVE".equals(reservation.status())) {
                    int changed = jdbc.update("""
                            UPDATE product_inventory
                            SET reserved_quantity = reserved_quantity - ?,
                                version = version + 1, updated_at = now()
                            WHERE product_id = ? AND reserved_quantity >= ?
                            """, reservation.quantity(), reservation.productId(), reservation.quantity());
                    if (changed != 1) {
                        throw new IllegalStateException("Reserved inventory is inconsistent");
                    }
                }
            }
            jdbc.update("""
                    UPDATE inventory_reservations SET status = 'RELEASED', updated_at = now()
                    WHERE order_id = ? AND status = 'ACTIVE'
                    """, payment.orderId());
            jdbc.update("""
                    UPDATE customer_orders SET status = 'CANCELLED', updated_at = now()
                    WHERE id = ? AND status = 'PENDING_PAYMENT'
                    """, payment.orderId());
            jdbc.update("""
                    UPDATE vendor_orders SET status = 'CANCELLED', updated_at = now()
                    WHERE order_id = ? AND status = 'PENDING_PAYMENT'
                    """, payment.orderId());
            jdbc.update("""
                    UPDATE payments SET status = 'FAILED', updated_at = now()
                    WHERE id = ? AND status = 'PROCESSING'
                    """, payment.id());
            markProcessed(eventId, "FAILED");
            return new WebhookResult(false, "FAILED");
        }
        throw new IllegalArgumentException("Unsupported payment event type");
    }

    private void markProcessed(String eventId, String outcome) {
        jdbc.update("""
                UPDATE payment_provider_events
                SET processed_at = now(), outcome = ?
                WHERE provider_code = ? AND event_id = ?
                """, outcome, PROVIDER_CODE, eventId);
    }

    private static boolean sameHash(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
