package com.markethub.vendor.catalog.api;

import com.markethub.vendor.application.ApprovedVendorAccess;
import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VendorProductService {
    private final JdbcTemplate jdbc;
    private final ApprovedVendorAccess access;

    public VendorProductService(JdbcTemplate jdbc, ApprovedVendorAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record ProductView(UUID id, String name, String slug, String description,
                              BigDecimal price, String currency, String status,
                              long version, Instant createdAt, Instant updatedAt) {}

    public List<ProductView> list(UUID userId) {
        UUID vendor = access.requireApprovedVendor(userId);
        return jdbc.query("""
                SELECT id,name,slug,description,price,currency,status,version,created_at,updated_at
                FROM products WHERE vendor_id=? AND status<>'ARCHIVED'
                ORDER BY created_at DESC
                """, (rs, n) -> view(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("slug"), rs.getString("description"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getString("status"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), vendor);
    }

    @Transactional
    public ProductView create(UUID userId, VendorProductRequest request) {
        UUID vendor = access.requireApprovedVendor(userId);
        UUID id = UUID.randomUUID();
        if (request.expectedVersion() != null) {
            throw new IllegalArgumentException("expectedVersion must be omitted when creating a product");
        }
        jdbc.update("""
                INSERT INTO products(id,vendor_id,name,slug,description,price,currency,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,'ACTIVE',now(),now())
                """, id, vendor, request.name().trim(), request.slug(), request.description(),
                request.price(), request.currency());
        jdbc.update("INSERT INTO product_inventory(product_id,vendor_id) VALUES (?,?)", id, vendor);
        return find(vendor, id);
    }

    @Transactional
    public ProductView update(UUID userId, UUID productId, VendorProductRequest request) {
        UUID vendor = access.requireApprovedVendor(userId);
        if (request.expectedVersion() == null) {
            throw new IllegalArgumentException("expectedVersion is required when updating a product");
        }
        ProductView current = find(vendor, productId);
        if (current.version() != request.expectedVersion()) {
            throw new OptimisticLockingFailureException("Product version is stale; reload the product and retry");
        }
        int changed = jdbc.update("""
                UPDATE products SET name=?,slug=?,description=?,price=?,currency=?,
                    updated_at=now(),version=version+1
                WHERE id=? AND vendor_id=? AND version=? AND status<>'ARCHIVED'
                """, request.name().trim(), request.slug(), request.description(), request.price(),
                request.currency(), productId, vendor, request.expectedVersion());
        if (changed != 1) {
            throw new OptimisticLockingFailureException("Product was changed concurrently or is archived");
        }
        return find(vendor, productId);
    }

    @Transactional
    public void archive(UUID userId, UUID productId) {
        UUID vendor = access.requireApprovedVendor(userId);
        int changed = jdbc.update("""
                UPDATE products SET status='ARCHIVED',archived_at=now(),updated_at=now(),version=version+1
                WHERE id=? AND vendor_id=? AND status<>'ARCHIVED'
                """, productId, vendor);
        if (changed != 1) {
            throw new ResourceNotFoundException("Product not found");
        }
    }

    private ProductView find(UUID vendor, UUID id) {
        return jdbc.query("""
                SELECT id,name,slug,description,price,currency,status,version,created_at,updated_at
                FROM products WHERE id=? AND vendor_id=?
                """, (rs, n) -> view(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("slug"), rs.getString("description"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getString("status"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                id, vendor).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ProductView view(UUID id, String name, String slug, String description, BigDecimal price,
                             String currency, String status, long version, Instant created, Instant updated) {
        return new ProductView(id, name, slug, description, price, currency, status, version, created, updated);
    }
}
