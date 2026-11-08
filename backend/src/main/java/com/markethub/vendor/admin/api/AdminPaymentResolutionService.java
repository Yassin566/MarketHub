package com.markethub.vendor.admin.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminPaymentResolutionService {
    private final JdbcTemplate jdbc;

    public AdminPaymentResolutionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ResolutionRequest(String decision, String evidenceReference, String reason) {}
    public record ResolutionView(UUID id, UUID paymentId, UUID actorUserId, String actorEmail,
                                 String decision, String previousPaymentStatus,
                                 String resultingPaymentStatus, String evidenceReference,
                                 String reason, Instant createdAt) {}
    private record PaymentState(UUID id, UUID orderId, String status) {}
    private record Reservation(UUID id, UUID productId, long quantity, String status, boolean unexpired) {}

    @Transactional
    public ResolutionView resolve(UUID paymentId, UUID actorUserId, ResolutionRequest request) {
        String decision = normalizeDecision(request);
        String evidence = requiredText(request.evidenceReference(), "evidenceReference", 300);
        String reason = requiredText(request.reason(), "reason", 500);

        List<PaymentState> payments = jdbc.query("""
                SELECT id, order_id, status FROM payments WHERE id = ? FOR UPDATE
                """, (rs, row) -> new PaymentState(
                rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getString("status")), paymentId);
        if (payments.isEmpty()) throw new ResourceNotFoundException("Payment was not found");
        PaymentState payment = payments.getFirst();
        if (!"REQUIRES_REVIEW".equals(payment.status())) {
            throw new IllegalStateException("Only payments requiring review can be manually resolved");
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

        if ("CONFIRM_SUCCEEDED".equals(decision)) {
            if (reservations.isEmpty() || reservations.stream()
                    .anyMatch(r -> !"ACTIVE".equals(r.status()) || !r.unexpired())) {
                throw new IllegalStateException(
                        "Payment success cannot be applied because inventory reservations are no longer valid");
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
                if (changed != 1) throw new IllegalStateException("Reserved inventory is inconsistent");
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
                    WHERE id = ? AND status = 'REQUIRES_REVIEW'
                    """, paymentId);
        } else {
            for (Reservation reservation : reservations) {
                if (!"ACTIVE".equals(reservation.status())) continue;
                int changed = jdbc.update("""
                        UPDATE product_inventory
                        SET reserved_quantity = reserved_quantity - ?,
                            version = version + 1, updated_at = now()
                        WHERE product_id = ? AND reserved_quantity >= ?
                        """, reservation.quantity(), reservation.productId(), reservation.quantity());
                if (changed != 1) throw new IllegalStateException("Reserved inventory is inconsistent");
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
                    WHERE id = ? AND status = 'REQUIRES_REVIEW'
                    """, paymentId);
        }

        UUID resolutionId = UUID.randomUUID();
        String resultingStatus = "CONFIRM_SUCCEEDED".equals(decision) ? "SUCCEEDED" : "FAILED";
        jdbc.update("""
                INSERT INTO payment_manual_resolutions
                    (id, payment_id, actor_user_id, decision, previous_payment_status,
                     resulting_payment_status, evidence_reference, reason)
                VALUES (?, ?, ?, ?, 'REQUIRES_REVIEW', ?, ?, ?)
                """, resolutionId, paymentId, actorUserId, decision, resultingStatus, evidence, reason);
        return getResolution(paymentId);
    }

    @Transactional(readOnly = true)
    public ResolutionView getResolution(UUID paymentId) {
        List<ResolutionView> rows = jdbc.query("""
                SELECT r.id, r.payment_id, r.actor_user_id, u.email AS actor_email,
                       r.decision, r.previous_payment_status, r.resulting_payment_status,
                       r.evidence_reference, r.reason, r.created_at
                FROM payment_manual_resolutions r
                JOIN app_users u ON u.id = r.actor_user_id
                WHERE r.payment_id = ?
                """, (rs, row) -> new ResolutionView(
                rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class), rs.getString("actor_email"),
                rs.getString("decision"), rs.getString("previous_payment_status"),
                rs.getString("resulting_payment_status"), rs.getString("evidence_reference"),
                rs.getString("reason"), rs.getTimestamp("created_at").toInstant()), paymentId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Manual payment resolution was not found");
        return rows.getFirst();
    }

    private static String normalizeDecision(ResolutionRequest request) {
        if (request == null || request.decision() == null) {
            throw new IllegalArgumentException("decision is required");
        }
        String decision = request.decision().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"CONFIRM_SUCCEEDED".equals(decision) && !"CONFIRM_FAILED".equals(decision)) {
            throw new IllegalArgumentException("decision must be CONFIRM_SUCCEEDED or CONFIRM_FAILED");
        }
        return decision;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxLength + " characters");
        }
        return value.trim();
    }
}
