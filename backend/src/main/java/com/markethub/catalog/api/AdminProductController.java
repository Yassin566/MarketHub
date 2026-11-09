package com.markethub.catalog.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.common.exception.ResourceNotFoundException;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminProductController {
    private final JdbcTemplate jdbc;
    public AdminProductController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ProductItem(UUID id, UUID vendorId, String vendorName, String name,
                              String slug, BigDecimal price, String currency, String status,
                              Instant createdAt, String lastActorEmail, String lastReason,
                              Instant lastActionAt) {}
    public record ProductPage(List<ProductItem> items, long total, int limit, int offset) {}
    public record StatusRequest(@NotBlank @Size(max=500) String reason) {}

    @GetMapping
    public ApiResponse<ProductPage> list(
            @RequestParam(required=false) String search,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="20") int limit,
            @RequestParam(defaultValue="0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new IllegalArgumentException("Invalid pagination");
        if (status != null && !List.of("ACTIVE", "INACTIVE", "DRAFT").contains(status))
            throw new IllegalArgumentException("Unsupported product status");
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (status != null) { where.append(" AND p.status = ? "); args.add(status); }
        if (search != null && !search.isBlank()) {
            where.append(" AND (p.name ILIKE ? OR v.display_name ILIKE ?) ");
            String term = "%" + search.trim() + "%"; args.add(term); args.add(term);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM products p JOIN vendors v ON v.id=p.vendor_id" + where,
                Long.class, args.toArray());
        String sql = """
            SELECT p.id,p.vendor_id,v.display_name AS vendor_name,p.name,p.slug,p.price,p.currency,
                   p.status,p.created_at,u.email AS last_actor_email,a.reason AS last_reason,
                   a.created_at AS last_action_at
            FROM products p JOIN vendors v ON v.id=p.vendor_id
            LEFT JOIN LATERAL (
                SELECT actor_user_id,reason,created_at FROM admin_product_moderation_events
                WHERE product_id=p.id ORDER BY created_at DESC,id DESC LIMIT 1
            ) a ON TRUE
            LEFT JOIN app_users u ON u.id=a.actor_user_id
            """ + where + " ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?";
        args.add(limit); args.add(offset);
        List<ProductItem> items = jdbc.query(sql, (rs,n) -> new ProductItem(
            rs.getObject("id",UUID.class), rs.getObject("vendor_id",UUID.class), rs.getString("vendor_name"),
            rs.getString("name"),rs.getString("slug"),rs.getBigDecimal("price"),rs.getString("currency"),
            rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getString("last_actor_email"),
            rs.getString("last_reason"),rs.getTimestamp("last_action_at")==null?null:rs.getTimestamp("last_action_at").toInstant()
        ), args.toArray());
        return ApiResponse.ok(new ProductPage(items,total==null?0:total,limit,offset));
    }

    @Transactional
    @PatchMapping("/{productId}/deactivation")
    public ApiResponse<ProductItem> deactivate(@PathVariable UUID productId,
            @AuthenticationPrincipal MarketHubPrincipal principal, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(changeStatus(productId, principal.userId(), "INACTIVE", request.reason()));
    }

    @Transactional
    @PatchMapping("/{productId}/activation")
    public ApiResponse<ProductItem> activate(@PathVariable UUID productId,
            @AuthenticationPrincipal MarketHubPrincipal principal, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(changeStatus(productId, principal.userId(), "ACTIVE", request.reason()));
    }

    private ProductItem changeStatus(UUID id, UUID actor, String target, String reason) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<ProductItem>) connection -> {
            try (var lock = connection.prepareStatement("SELECT status FROM products WHERE id=? FOR UPDATE")) {
                lock.setObject(1,id);
                try (var rs=lock.executeQuery()) {
                    if (!rs.next()) throw new ResourceNotFoundException("Product not found");
                    String previous=rs.getString(1);
                    if (!previous.equals(target)) {
                        try (var update=connection.prepareStatement("UPDATE products SET status=?,version=version+1 WHERE id=?")) {
                            update.setString(1,target); update.setObject(2,id); update.executeUpdate();
                        }
                        try (var audit=connection.prepareStatement("""
                            INSERT INTO admin_product_moderation_events
                            (id,product_id,actor_user_id,previous_status,resulting_status,reason)
                            VALUES (?,?,?,?,?,?)
                            """)) {
                            audit.setObject(1,UUID.randomUUID()); audit.setObject(2,id); audit.setObject(3,actor);
                            audit.setString(4,previous); audit.setString(5,target); audit.setString(6,reason.trim());
                            audit.executeUpdate();
                        }
                    }
                }
            }
            return jdbc.queryForObject("""
                SELECT p.id,p.vendor_id,v.display_name,p.name,p.slug,p.price,p.currency,p.status,p.created_at,
                       u.email,a.reason,a.created_at
                FROM products p JOIN vendors v ON v.id=p.vendor_id
                LEFT JOIN LATERAL (SELECT actor_user_id,reason,created_at FROM admin_product_moderation_events
                    WHERE product_id=p.id ORDER BY created_at DESC,id DESC LIMIT 1) a ON TRUE
                LEFT JOIN app_users u ON u.id=a.actor_user_id WHERE p.id=?
                """, (rs,n)->new ProductItem(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getString(7),rs.getString(8),
                rs.getTimestamp(9).toInstant(),rs.getString(10),rs.getString(11),
                rs.getTimestamp(12)==null?null:rs.getTimestamp(12).toInstant()),id);
        });
    }
}
