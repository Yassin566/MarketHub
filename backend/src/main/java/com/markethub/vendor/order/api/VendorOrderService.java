package com.markethub.vendor.order.api;

import com.markethub.common.exception.ResourceNotFoundException;
import com.markethub.vendor.application.ApprovedVendorAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VendorOrderService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final ApprovedVendorAccess access;

    public VendorOrderService(JdbcTemplate jdbc, ApprovedVendorAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record OrderLine(UUID id, UUID productId, String productName, long quantity,
                            BigDecimal unitPrice, String currency, BigDecimal lineTotal) {}
    public record StatusChange(UUID id, String previousStatus, String newStatus,
                               String note, Instant createdAt) {}
    public record OrderView(UUID id, UUID customerOrderId, String status, String currency,
                            BigDecimal subtotal, Instant createdAt, List<OrderLine> items,
                            List<StatusChange> statusHistory) {}
    public record OrderPage(List<OrderView> orders, int limit, int offset, boolean hasMore) {}
    private record CurrentOrder(String status) {}

    @Transactional(readOnly = true)
    public OrderPage list(UUID userId, int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        UUID vendorId = access.requireApprovedVendor(userId);
        List<UUID> ids = jdbc.query("""
                SELECT id FROM vendor_orders
                WHERE vendor_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> rs.getObject("id", UUID.class), vendorId, limit + 1, offset);
        boolean hasMore = ids.size() > limit;
        List<OrderView> orders = ids.stream().limit(limit)
                .map(id -> load(vendorId, id)).toList();
        return new OrderPage(orders, limit, offset, hasMore);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID userId, UUID vendorOrderId) {
        UUID vendorId = access.requireApprovedVendor(userId);
        return load(vendorId, vendorOrderId);
    }

    @Transactional
    public OrderView updateStatus(UUID userId, UUID vendorOrderId, String requestedStatus, String note) {
        UUID vendorId = access.requireApprovedVendor(userId);
        List<CurrentOrder> rows = jdbc.query("""
                SELECT status FROM vendor_orders
                WHERE id = ? AND vendor_id = ?
                FOR UPDATE
                """, (rs, row) -> new CurrentOrder(rs.getString("status")), vendorOrderId, vendorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Vendor order not found");

        String current = rows.getFirst().status();
        boolean allowed = ("PAID".equals(current) && "PROCESSING".equals(requestedStatus))
                || ("PROCESSING".equals(current) && "FULFILLED".equals(requestedStatus));
        if (!allowed) {
            throw new IllegalStateException("Invalid fulfilment transition from " + current + " to " + requestedStatus);
        }

        int changed = jdbc.update("""
                UPDATE vendor_orders SET status = ?, updated_at = now()
                WHERE id = ? AND vendor_id = ? AND status = ?
                """, requestedStatus, vendorOrderId, vendorId, current);
        if (changed != 1) throw new IllegalStateException("Vendor order changed concurrently; reload and retry");

        jdbc.update("""
                INSERT INTO vendor_order_status_history
                    (id, vendor_order_id, vendor_id, actor_user_id, previous_status, new_status, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), vendorOrderId, vendorId, userId, current, requestedStatus, note.trim());
        return load(vendorId, vendorOrderId);
    }

    private OrderView load(UUID vendorId, UUID vendorOrderId) {
        List<OrderView> rows = jdbc.query("""
                SELECT id, order_id, status, currency, subtotal, created_at
                FROM vendor_orders
                WHERE id = ? AND vendor_id = ?
                """, (rs, row) -> new OrderView(
                rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getString("status"), rs.getString("currency"), rs.getBigDecimal("subtotal"),
                rs.getTimestamp("created_at").toInstant(), List.of(), List.of()),
                vendorOrderId, vendorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Vendor order not found");
        OrderView header = rows.getFirst();
        List<OrderLine> items = jdbc.query("""
                SELECT id, product_id, product_name, quantity, unit_price, currency, line_total
                FROM order_items
                WHERE vendor_order_id = ? AND vendor_id = ?
                ORDER BY id
                """, (rs, row) -> new OrderLine(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("product_name"), rs.getLong("quantity"), rs.getBigDecimal("unit_price"),
                rs.getString("currency"), rs.getBigDecimal("line_total")), vendorOrderId, vendorId);
        List<StatusChange> history = jdbc.query("""
                SELECT id, previous_status, new_status, note, created_at
                FROM vendor_order_status_history
                WHERE vendor_order_id = ? AND vendor_id = ?
                ORDER BY created_at DESC, id DESC
                """, (rs, row) -> new StatusChange(
                rs.getObject("id", UUID.class), rs.getString("previous_status"),
                rs.getString("new_status"), rs.getString("note"),
                rs.getTimestamp("created_at").toInstant()), vendorOrderId, vendorId);
        return new OrderView(header.id(), header.customerOrderId(), header.status(),
                header.currency(), header.subtotal(), header.createdAt(),
                List.copyOf(items), List.copyOf(history));
    }
}
