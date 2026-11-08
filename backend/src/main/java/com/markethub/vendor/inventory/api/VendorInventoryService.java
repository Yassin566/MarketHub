package com.markethub.vendor.inventory.api;

import com.markethub.common.exception.ResourceNotFoundException;
import com.markethub.vendor.application.ApprovedVendorAccess;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VendorInventoryService {

    private final JdbcTemplate jdbc;
    private final ApprovedVendorAccess vendorAccess;

    public VendorInventoryService(JdbcTemplate jdbc, ApprovedVendorAccess vendorAccess) {
        this.jdbc = jdbc;
        this.vendorAccess = vendorAccess;
    }

    public record InventoryView(
            UUID productId,
            String productName,
            long onHandQuantity,
            long reservedQuantity,
            long availableQuantity,
            long version,
            Instant updatedAt
    ) {}

    public record AdjustmentView(
            UUID id,
            UUID productId,
            long quantityDelta,
            long quantityBefore,
            long quantityAfter,
            String reason,
            long versionAfter,
            Instant createdAt
    ) {}

    private static final RowMapper<InventoryView> INVENTORY_MAPPER = (rs, row) ->
            new InventoryView(
                    rs.getObject("product_id", UUID.class),
                    rs.getString("product_name"),
                    rs.getLong("on_hand_quantity"),
                    rs.getLong("reserved_quantity"),
                    rs.getLong("on_hand_quantity") - rs.getLong("reserved_quantity"),
                    rs.getLong("version"),
                    rs.getTimestamp("updated_at").toInstant()
            );

    private static final RowMapper<AdjustmentView> ADJUSTMENT_MAPPER = (rs, row) ->
            new AdjustmentView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("product_id", UUID.class),
                    rs.getLong("quantity_delta"),
                    rs.getLong("quantity_before"),
                    rs.getLong("quantity_after"),
                    rs.getString("reason"),
                    rs.getLong("version_after"),
                    rs.getTimestamp("created_at").toInstant()
            );

    public List<InventoryView> list(UUID userId) {
        UUID vendorId = vendorAccess.requireApprovedVendor(userId);
        return jdbc.query("""
                SELECT i.product_id, p.name AS product_name, i.on_hand_quantity,
                       i.reserved_quantity, i.version, i.updated_at
                FROM product_inventory i
                JOIN products p ON p.id = i.product_id
                WHERE i.vendor_id = ? AND p.status <> 'ARCHIVED'
                ORDER BY p.name, i.product_id
                """, INVENTORY_MAPPER, vendorId);
    }

    @Transactional
    public AdjustmentView adjust(UUID userId, UUID productId, InventoryAdjustmentRequest request) {
        UUID vendorId = vendorAccess.requireApprovedVendor(userId);
        long delta = request.quantityDelta();
        if (delta == 0) {
            throw new IllegalArgumentException("quantityDelta must not be zero");
        }

        List<InventoryView> updated = jdbc.query("""
                UPDATE product_inventory
                SET on_hand_quantity = on_hand_quantity + ?,
                    version = version + 1,
                    updated_at = now()
                WHERE product_id = ?
                  AND vendor_id = ?
                  AND version = ?
                  AND on_hand_quantity + ? >= reserved_quantity
                RETURNING product_id, on_hand_quantity, reserved_quantity, version, updated_at,
                          (SELECT name FROM products WHERE id = product_inventory.product_id) AS product_name
                """, INVENTORY_MAPPER, delta, productId, vendorId, request.expectedVersion(), delta);

        if (updated.isEmpty()) {
            InventoryView current = jdbc.query("""
                    SELECT i.product_id, p.name AS product_name, i.on_hand_quantity,
                           i.reserved_quantity, i.version, i.updated_at
                    FROM product_inventory i
                    JOIN products p ON p.id = i.product_id
                    WHERE i.product_id = ? AND i.vendor_id = ?
                    """, INVENTORY_MAPPER, productId, vendorId)
                    .stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

            if (current.version() != request.expectedVersion()) {
                throw new OptimisticLockingFailureException(
                        "Inventory was changed; reload the current quantity and retry");
            }
            throw new IllegalStateException("Stock cannot be reduced below the reserved quantity");
        }

        InventoryView result = updated.getFirst();
        long before = result.onHandQuantity() - delta;
        UUID adjustmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_adjustments
                    (id, product_id, vendor_id, actor_user_id, quantity_delta,
                     quantity_before, quantity_after, reason, version_after)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                adjustmentId, productId, vendorId, userId, delta, before,
                result.onHandQuantity(), request.reason(), result.version());

        return new AdjustmentView(adjustmentId, productId, delta, before,
                result.onHandQuantity(), request.reason(), result.version(), Instant.now());
    }

    public List<AdjustmentView> history(UUID userId, UUID productId) {
        UUID vendorId = vendorAccess.requireApprovedVendor(userId);
        List<AdjustmentView> history = jdbc.query("""
                SELECT id, product_id, quantity_delta, quantity_before, quantity_after,
                       reason, version_after, created_at
                FROM inventory_adjustments
                WHERE product_id = ? AND vendor_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, ADJUSTMENT_MAPPER, productId, vendorId);

        // An empty ledger is valid only when the product belongs to this vendor.
        if (history.isEmpty()) {
            Integer owned = jdbc.query("""
                    SELECT 1 FROM product_inventory
                    WHERE product_id = ? AND vendor_id = ?
                    """, rs -> rs.next() ? 1 : null, productId, vendorId);
            if (owned == null) {
                throw new ResourceNotFoundException("Inventory not found");
            }
        }
        return history;
    }
}
