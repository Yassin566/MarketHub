package com.markethub.customer.order.api;

import com.markethub.common.exception.ResourceNotFoundException;
import com.markethub.customer.reservation.api.CustomerInventoryReservationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class CustomerOrderService {
    private static final int ORDER_RESERVATION_TTL_MINUTES = 15;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final CustomerInventoryReservationService reservations;

    public CustomerOrderService(JdbcTemplate jdbc, CustomerInventoryReservationService reservations) {
        this.jdbc = jdbc;
        this.reservations = reservations;
    }

    public record OrderLine(UUID productId, UUID vendorId, String productName, long quantity,
                            BigDecimal unitPrice, String currency, BigDecimal lineTotal) {}
    public record OrderView(UUID id, String status, String currency, BigDecimal subtotal,
                            BigDecimal total, Instant expiresAt, Instant createdAt, List<OrderLine> items) {}
    public record OrderPage(List<OrderView> orders, int limit, int offset, boolean hasMore) {}
    private record CartLine(UUID productId, UUID vendorId, String productName, long quantity,
                            BigDecimal price, String currency, long onHand, long reserved,
                            String productStatus, String vendorStatus) {}

    @Transactional
    public OrderView createFromCart(UUID customerId, UUID idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "Idempotency-Key is required");
        reservations.expireDueReservations();
        OrderView previous = findByIdempotencyKey(customerId, idempotencyKey);
        if (previous != null) return previous;

        UUID cartId = jdbc.queryForObject("""
                INSERT INTO customer_carts (id, customer_user_id)
                VALUES (?, ?)
                ON CONFLICT (customer_user_id) DO UPDATE SET updated_at = customer_carts.updated_at
                RETURNING id
                """, UUID.class, UUID.randomUUID(), customerId);
        jdbc.queryForObject("""
                SELECT id FROM customer_carts
                WHERE id = ? AND customer_user_id = ?
                FOR UPDATE
                """, UUID.class, cartId, customerId);

        previous = findByIdempotencyKey(customerId, idempotencyKey);
        if (previous != null) return previous;

        List<CartLine> lines = jdbc.query("""
                SELECT p.id AS product_id, p.vendor_id, p.name AS product_name,
                       ci.quantity, p.price, p.currency, i.on_hand_quantity, i.reserved_quantity,
                       p.status AS product_status, v.status AS vendor_status
                FROM customer_cart_items ci
                JOIN products p ON p.id = ci.product_id
                JOIN vendors v ON v.id = p.vendor_id
                JOIN product_inventory i ON i.product_id = p.id
                WHERE ci.cart_id = ?
                ORDER BY p.id
                FOR UPDATE OF p, v, i
                """, (rs, row) -> new CartLine(
                rs.getObject("product_id", UUID.class), rs.getObject("vendor_id", UUID.class),
                rs.getString("product_name"), rs.getLong("quantity"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getLong("on_hand_quantity"), rs.getLong("reserved_quantity"),
                rs.getString("product_status"), rs.getString("vendor_status")), cartId);
        if (lines.isEmpty()) throw new IllegalStateException("Cart is empty");

        String currency = null;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            if (!"ACTIVE".equals(line.productStatus())) throw new IllegalStateException(line.productName() + " is no longer active");
            if (!"APPROVED".equals(line.vendorStatus())) throw new IllegalStateException("A vendor in this cart is not approved");
            if (line.onHand() - line.reserved() < line.quantity()) throw new IllegalStateException("Insufficient available stock for " + line.productName());
            if (currency != null && !currency.equals(line.currency())) throw new IllegalStateException("Cart contains products in different currencies");
            currency = line.currency();
            subtotal = subtotal.add(line.price().multiply(BigDecimal.valueOf(line.quantity())));
        }
        final String orderCurrency = currency;

        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_orders
                    (id, customer_user_id, idempotency_key, status, currency, subtotal, total)
                VALUES (?, ?, ?, 'PENDING_PAYMENT', ?, ?, ?)
                """, orderId, customerId, idempotencyKey, orderCurrency, subtotal, subtotal);

        Map<UUID, UUID> vendorOrderIds = new LinkedHashMap<>();
        Map<UUID, BigDecimal> vendorSubtotals = new LinkedHashMap<>();
        for (CartLine line : lines) {
            vendorOrderIds.computeIfAbsent(line.vendorId(), vendorId -> {
                UUID vendorOrderId = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO vendor_orders (id, order_id, vendor_id, status, currency, subtotal)
                        VALUES (?, ?, ?, 'PENDING_PAYMENT', ?, 0)
                        """, vendorOrderId, orderId, vendorId, orderCurrency);
                vendorSubtotals.put(vendorId, BigDecimal.ZERO);
                return vendorOrderId;
            });
            BigDecimal lineTotal = line.price().multiply(BigDecimal.valueOf(line.quantity()));
            jdbc.update("""
                    INSERT INTO order_items
                        (id, order_id, vendor_order_id, product_id, vendor_id, product_name,
                         quantity, unit_price, currency, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), orderId, vendorOrderIds.get(line.vendorId()),
                    line.productId(), line.vendorId(), line.productName(), line.quantity(),
                    line.price(), line.currency(), lineTotal);
            vendorSubtotals.put(line.vendorId(), vendorSubtotals.get(line.vendorId()).add(lineTotal));

            int stockUpdated = jdbc.update("""
                    UPDATE product_inventory
                    SET reserved_quantity = reserved_quantity + ?, version = version + 1, updated_at = now()
                    WHERE product_id = ? AND on_hand_quantity - reserved_quantity >= ?
                    """, line.quantity(), line.productId(), line.quantity());
            if (stockUpdated != 1) throw new IllegalStateException("Inventory changed during checkout; retry");
            jdbc.update("""
                    INSERT INTO inventory_reservations
                        (id, customer_user_id, product_id, vendor_id, order_id,
                         quantity, status, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', now() + (? * interval '1 minute'))
                    """, UUID.randomUUID(), customerId, line.productId(), line.vendorId(),
                    orderId, line.quantity(), ORDER_RESERVATION_TTL_MINUTES);
        }
        for (Map.Entry<UUID, BigDecimal> entry : vendorSubtotals.entrySet()) {
            jdbc.update("UPDATE vendor_orders SET subtotal = ? WHERE id = ?", entry.getValue(), vendorOrderIds.get(entry.getKey()));
        }
        jdbc.update("DELETE FROM customer_cart_items WHERE cart_id = ?", cartId);
        jdbc.update("UPDATE customer_carts SET version = version + 1, updated_at = now() WHERE id = ?", cartId);
        return loadOrder(customerId, orderId);
    }

    @Transactional(readOnly = true)
    public OrderPage list(UUID customerId, int limit, int offset) {
        if (limit < 1 || limit > MAX_HISTORY_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_HISTORY_PAGE_SIZE);
        }
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        List<UUID> ids = jdbc.query("""
                SELECT id FROM customer_orders
                WHERE customer_user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> rs.getObject("id", UUID.class), customerId, limit + 1, offset);
        boolean hasMore = ids.size() > limit;
        List<OrderView> page = ids.stream().limit(limit)
                .map(id -> loadOrder(customerId, id)).toList();
        return new OrderPage(page, limit, offset, hasMore);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID customerId, UUID orderId) {
        OrderView order = loadOrderOrNull(customerId, orderId);
        if (order == null) throw new ResourceNotFoundException("Order not found");
        return order;
    }

    private OrderView findByIdempotencyKey(UUID customerId, UUID key) {
        List<UUID> ids = jdbc.query("SELECT id FROM customer_orders WHERE customer_user_id = ? AND idempotency_key = ?",
                (rs, row) -> rs.getObject("id", UUID.class), customerId, key);
        return ids.isEmpty() ? null : loadOrder(customerId, ids.getFirst());
    }
    private OrderView loadOrder(UUID customerId, UUID orderId) {
        OrderView order = loadOrderOrNull(customerId, orderId);
        if (order == null) throw new ResourceNotFoundException("Order not found");
        return order;
    }
    private OrderView loadOrderOrNull(UUID customerId, UUID orderId) {
        List<OrderView> orders = jdbc.query("""
                SELECT id, status, currency, subtotal, total, created_at,
                       (SELECT max(expires_at) FROM inventory_reservations WHERE order_id = customer_orders.id) AS expires_at
                FROM customer_orders WHERE id = ? AND customer_user_id = ?
                """, (rs, row) -> new OrderView(
                rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("currency"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("total"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), List.of()), orderId, customerId);
        if (orders.isEmpty()) return null;
        OrderView header = orders.getFirst();
        List<OrderLine> items = jdbc.query("""
                SELECT product_id, vendor_id, product_name, quantity, unit_price, currency, line_total
                FROM order_items WHERE order_id = ? ORDER BY id
                """, (rs, row) -> new OrderLine(
                rs.getObject("product_id", UUID.class), rs.getObject("vendor_id", UUID.class),
                rs.getString("product_name"), rs.getLong("quantity"), rs.getBigDecimal("unit_price"),
                rs.getString("currency"), rs.getBigDecimal("line_total")), orderId);
        return new OrderView(header.id(), header.status(), header.currency(), header.subtotal(),
                header.total(), header.expiresAt(), header.createdAt(), List.copyOf(items));
    }
}
