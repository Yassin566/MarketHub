package com.markethub.customer.reservation.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerInventoryReservationService {
    private static final int RESERVATION_TTL_MINUTES = 15;
    private static final int EXPIRY_BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;

    public CustomerInventoryReservationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ReservationView(
            UUID id, UUID productId, String productName, long quantity,
            String status, Instant expiresAt, Instant createdAt
    ) {}

    @Transactional
    public ReservationView reserve(UUID customerId, UUID productId, long quantity) {
        if (quantity < 1 || quantity > 1000) {
            throw new IllegalArgumentException("quantity must be between 1 and 1000");
        }

        expireDueReservations();
        var products = jdbc.query("""
                SELECT i.vendor_id, i.on_hand_quantity, i.reserved_quantity,
                       p.name AS product_name, p.status AS product_status,
                       v.status AS vendor_status
                FROM product_inventory i
                JOIN products p ON p.id = i.product_id
                JOIN vendors v ON v.id = i.vendor_id
                WHERE i.product_id = ?
                FOR UPDATE OF i
                """, (rs, row) -> new ProductStock(
                rs.getObject("vendor_id", UUID.class),
                rs.getLong("on_hand_quantity"),
                rs.getLong("reserved_quantity"),
                rs.getString("product_name"),
                rs.getString("product_status"),
                rs.getString("vendor_status")), productId);

        if (products.isEmpty()
                || !"ACTIVE".equals(products.getFirst().productStatus())
                || !"APPROVED".equals(products.getFirst().vendorStatus())) {
            throw new ResourceNotFoundException("Product not found");
        }

        ProductStock stock = products.getFirst();
        if (stock.onHand() - stock.reserved() < quantity) {
            throw new IllegalStateException("Insufficient available inventory");
        }

        UUID reservationId = UUID.randomUUID();
        ReservationView reservation = jdbc.queryForObject("""
                INSERT INTO inventory_reservations
                    (id, customer_user_id, product_id, vendor_id, quantity, status, expires_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', now() + (? * interval '1 minute'))
                RETURNING id, product_id, ? AS product_name, quantity, status, expires_at, created_at
                """, RESERVATION_MAPPER, reservationId, customerId, productId,
                stock.vendorId(), quantity, RESERVATION_TTL_MINUTES, stock.productName());

        jdbc.update("""
                UPDATE product_inventory
                SET reserved_quantity = reserved_quantity + ?, version = version + 1, updated_at = now()
                WHERE product_id = ? AND vendor_id = ?
                """, quantity, productId, stock.vendorId());

        return reservation;
    }

    @Transactional
    public ReservationView release(UUID customerId, UUID reservationId) {
        List<ReservationView> rows = jdbc.query("""
                SELECT r.id, r.product_id, p.name AS product_name, r.quantity, r.status,
                       r.expires_at, r.created_at
                FROM inventory_reservations r
                JOIN products p ON p.id = r.product_id
                WHERE r.id = ? AND r.customer_user_id = ?
                FOR UPDATE OF r
                """, RESERVATION_MAPPER, reservationId, customerId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Reservation not found");
        }

        ReservationView reservation = rows.getFirst();
        if (!"ACTIVE".equals(reservation.status())) {
            return reservation;
        }

        jdbc.update("""
                UPDATE product_inventory
                SET reserved_quantity = reserved_quantity - ?, version = version + 1, updated_at = now()
                WHERE product_id = ? AND reserved_quantity >= ?
                """, reservation.quantity(), reservation.productId(), reservation.quantity());

        String nextStatus = reservation.expiresAt().isBefore(Instant.now()) ? "EXPIRED" : "RELEASED";
        return jdbc.queryForObject("""
                UPDATE inventory_reservations
                SET status = ?, updated_at = now()
                WHERE id = ?
                RETURNING id, product_id, ? AS product_name, quantity, status, expires_at, created_at
                """, RESERVATION_MAPPER, nextStatus, reservationId, reservation.productName());
    }

    public List<ReservationView> list(UUID customerId) {
        expireDueReservations();
        return jdbc.query("""
                SELECT r.id, r.product_id, p.name AS product_name, r.quantity, r.status,
                       r.expires_at, r.created_at
                FROM inventory_reservations r
                JOIN products p ON p.id = r.product_id
                WHERE r.customer_user_id = ?
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT 100
                """, RESERVATION_MAPPER, customerId);
    }

    @Scheduled(fixedDelayString = "${markethub.inventory.reservation-expiry-delay-ms:30000}")
    @Transactional
    public void expireDueReservations() {
        List<ExpiredReservation> expired = jdbc.query("""
                SELECT id, product_id, quantity
                FROM inventory_reservations
                WHERE status = 'ACTIVE' AND expires_at <= now()
                ORDER BY expires_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """, (rs, row) -> new ExpiredReservation(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getLong("quantity")), EXPIRY_BATCH_SIZE);

        for (ExpiredReservation reservation : expired) {
            int changed = jdbc.update("""
                    UPDATE inventory_reservations
                    SET status = 'EXPIRED', updated_at = now()
                    WHERE id = ? AND status = 'ACTIVE'
                    """, reservation.id());
            if (changed == 1) {
                jdbc.update("""
                        UPDATE product_inventory
                        SET reserved_quantity = reserved_quantity - ?, version = version + 1, updated_at = now()
                        WHERE product_id = ? AND reserved_quantity >= ?
                        """, reservation.quantity(), reservation.productId(), reservation.quantity());
            }
        }
    }

    private record ProductStock(UUID vendorId, long onHand, long reserved,
                                String productName, String productStatus, String vendorStatus) {}
    private record ExpiredReservation(UUID id, UUID productId, long quantity) {}

    private static final org.springframework.jdbc.core.RowMapper<ReservationView> RESERVATION_MAPPER =
            (rs, row) -> new ReservationView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("product_id", UUID.class),
                    rs.getString("product_name"),
                    rs.getLong("quantity"),
                    rs.getString("status"),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant()
            );
}
