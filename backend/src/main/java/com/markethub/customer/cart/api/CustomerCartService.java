package com.markethub.customer.cart.api;
import com.markethub.common.exception.ResourceNotFoundException;
import com.markethub.customer.reservation.api.CustomerInventoryReservationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Service
public class CustomerCartService {
    private final JdbcTemplate jdbc;
    private final CustomerInventoryReservationService reservations;
    public CustomerCartService(JdbcTemplate jdbc, CustomerInventoryReservationService reservations) {
        this.jdbc = jdbc; this.reservations = reservations;
    }
    public record CartItemView(UUID productId, String productName, long quantity, BigDecimal unitPrice,
            String currency, long availableQuantity, String productStatus, String vendorStatus, Instant updatedAt) {}
    public record CartView(UUID id, String status, long version, List<CartItemView> items,
            Instant createdAt, Instant updatedAt) {}
    public record CheckoutLine(UUID productId, String productName, long quantity, BigDecimal unitPrice,
            String currency, BigDecimal lineTotal, boolean eligible, String issue) {}
    public record CheckoutValidation(UUID cartId, boolean valid, List<CheckoutLine> lines,
            BigDecimal subtotal, String currency, List<String> issues) {}
    private record CartRow(UUID id, String status, long version, Instant createdAt, Instant updatedAt) {}
    private static final org.springframework.jdbc.core.RowMapper<CartItemView> ITEM_MAPPER = (rs, row) ->
            new CartItemView(rs.getObject("product_id", UUID.class), rs.getString("product_name"),
                    rs.getLong("quantity"), rs.getBigDecimal("price"), rs.getString("currency"),
                    rs.getLong("available_quantity"), rs.getString("product_status"),
                    rs.getString("vendor_status"), rs.getTimestamp("item_updated_at").toInstant());

    @Transactional
    public CartView get(UUID customerId) { return loadCart(getOrCreateCart(customerId)); }

    @Transactional
    public CartView putItem(UUID customerId, UUID productId, long quantity) {
        if (quantity < 1 || quantity > 1000) throw new IllegalArgumentException("quantity must be between 1 and 1000");
        Integer sellable = jdbc.query("""
                SELECT 1 FROM products p JOIN vendors v ON v.id = p.vendor_id
                WHERE p.id = ? AND p.status = 'ACTIVE' AND v.status = 'APPROVED'
                """, rs -> rs.next() ? 1 : null, productId);
        if (sellable == null) throw new ResourceNotFoundException("Product not found");
        UUID cartId = getOrCreateCart(customerId);
        jdbc.update("""
                INSERT INTO customer_cart_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, ?)
                ON CONFLICT (cart_id, product_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()
                """, UUID.randomUUID(), cartId, productId, quantity);
        jdbc.update("UPDATE customer_carts SET version = version + 1, updated_at = now() WHERE id = ?", cartId);
        return loadCart(cartId);
    }

    @Transactional
    public CartView removeItem(UUID customerId, UUID productId) {
        UUID cartId = getOrCreateCart(customerId);
        int removed = jdbc.update("DELETE FROM customer_cart_items WHERE cart_id = ? AND product_id = ?", cartId, productId);
        if (removed == 1) jdbc.update("UPDATE customer_carts SET version = version + 1, updated_at = now() WHERE id = ?", cartId);
        return loadCart(cartId);
    }

    @Transactional
    public CheckoutValidation validateCheckout(UUID customerId) {
        reservations.expireDueReservations();
        UUID cartId = getOrCreateCart(customerId);
        CartView cart = loadCart(cartId);
        if (cart.items().isEmpty()) return new CheckoutValidation(cartId, false, List.of(), BigDecimal.ZERO, null,
                List.of("Cart is empty"));
        List<CheckoutLine> lines = new java.util.ArrayList<>();
        List<String> issues = new java.util.ArrayList<>();
        String currency = null;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItemView item : cart.items()) {
            String issue = null;
            if (!"ACTIVE".equals(item.productStatus())) issue = "Product is no longer active";
            else if (!"APPROVED".equals(item.vendorStatus())) issue = "Vendor is not approved";
            else if (item.availableQuantity() < item.quantity()) issue = "Insufficient available stock";
            else if (currency != null && !currency.equals(item.currency())) issue = "Cart contains products in different currencies";
            BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            boolean eligible = issue == null;
            if (eligible) {
                if (currency == null) currency = item.currency();
                subtotal = subtotal.add(lineTotal);
            } else issues.add(item.productName() + ": " + issue);
            lines.add(new CheckoutLine(item.productId(), item.productName(), item.quantity(), item.unitPrice(),
                    item.currency(), lineTotal, eligible, issue));
        }
        return new CheckoutValidation(cartId, issues.isEmpty(), List.copyOf(lines), subtotal, currency, List.copyOf(issues));
    }

    private UUID getOrCreateCart(UUID customerId) {
        return jdbc.queryForObject("""
                INSERT INTO customer_carts (id, customer_user_id) VALUES (?, ?)
                ON CONFLICT (customer_user_id) DO UPDATE SET updated_at = customer_carts.updated_at
                RETURNING id
                """, UUID.class, UUID.randomUUID(), customerId);
    }

    private CartView loadCart(UUID cartId) {
        CartRow cart = jdbc.queryForObject("""
                SELECT id, status, version, created_at, updated_at FROM customer_carts WHERE id = ?
                """, (rs, row) -> new CartRow(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), cartId);
        List<CartItemView> items = jdbc.query("""
                SELECT ci.product_id, p.name AS product_name, ci.quantity, p.price, p.currency,
                       p.status AS product_status, v.status AS vendor_status,
                       COALESCE(i.on_hand_quantity - i.reserved_quantity, 0) AS available_quantity,
                       ci.updated_at AS item_updated_at
                FROM customer_cart_items ci
                JOIN products p ON p.id = ci.product_id
                JOIN vendors v ON v.id = p.vendor_id
                LEFT JOIN product_inventory i ON i.product_id = p.id
                WHERE ci.cart_id = ? ORDER BY ci.created_at, ci.id
                """, ITEM_MAPPER, cartId);
        return new CartView(cart.id(), cart.status(), cart.version(), List.copyOf(items), cart.createdAt(), cart.updatedAt());
    }
}
