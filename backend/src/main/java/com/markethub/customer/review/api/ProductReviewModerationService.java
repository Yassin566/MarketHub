package com.markethub.customer.review.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProductReviewModerationService {
    private static final int MAX_LIMIT = 100;
    private final JdbcTemplate jdbc;

    public ProductReviewModerationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ModerationView(
            UUID id, UUID productId, String productName, int rating, String reviewText,
            boolean published, UUID customerUserId, Instant createdAt,
            String lastAction, String lastActorEmail, Instant lastActionAt) {}
    public record ModerationPage(List<ModerationView> items, long total, int limit, int offset) {}

    @Transactional(readOnly = true)
    public ModerationPage list(Boolean published, Integer rating, UUID productId, int limit, int offset) {
        validateFilters(rating, limit, offset);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (published != null) { where.append(" AND r.is_published = ? "); args.add(published); }
        if (rating != null) { where.append(" AND r.rating = ? "); args.add(rating); }
        if (productId != null) { where.append(" AND r.product_id = ? "); args.add(productId); }
        Long total = jdbc.queryForObject("SELECT count(*) FROM product_reviews r" + where, Long.class, args.toArray());
        String sql = """
                SELECT r.id, r.product_id, p.name AS product_name, r.rating, r.review_text,
                       r.is_published, r.customer_user_id, r.created_at,
                       audit.action AS last_action, u.email AS last_actor_email,
                       audit.created_at AS last_action_at
                FROM product_reviews r
                JOIN products p ON p.id = r.product_id
                LEFT JOIN LATERAL (
                    SELECT action, actor_user_id, created_at
                    FROM product_review_moderation_events
                    WHERE review_id = r.id
                    ORDER BY created_at DESC, id DESC LIMIT 1
                ) audit ON TRUE
                LEFT JOIN app_users u ON u.id = audit.actor_user_id
                """ + where + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);
        List<ModerationView> items = jdbc.query(sql, (rs, row) -> new ModerationView(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("product_name"), rs.getInt("rating"), rs.getString("review_text"),
                rs.getBoolean("is_published"), rs.getObject("customer_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getString("last_action"),
                rs.getString("last_actor_email"),
                rs.getTimestamp("last_action_at") == null ? null : rs.getTimestamp("last_action_at").toInstant()
        ), args.toArray());
        return new ModerationPage(items, total == null ? 0 : total, limit, offset);
    }

    @Transactional
    public ModerationView setPublished(UUID reviewId, UUID actorUserId, boolean published) {
        List<Boolean> current = jdbc.query(
                "SELECT is_published FROM product_reviews WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getBoolean(1), reviewId);
        if (current.isEmpty()) throw new ResourceNotFoundException("Review not found");
        boolean wasPublished = current.getFirst();
        if (wasPublished != published) {
            jdbc.update("UPDATE product_reviews SET is_published = ?, updated_at = now() WHERE id = ?",
                    published, reviewId);
            jdbc.update("""
                    INSERT INTO product_review_moderation_events (id, review_id, actor_user_id, action)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), reviewId, actorUserId, published ? "PUBLISHED" : "UNPUBLISHED");
        }
        return listOne(reviewId);
    }

    private ModerationView listOne(UUID reviewId) {
        List<ModerationView> rows = jdbc.query("""
                SELECT r.id, r.product_id, p.name AS product_name, r.rating, r.review_text,
                       r.is_published, r.customer_user_id, r.created_at,
                       audit.action AS last_action, u.email AS last_actor_email,
                       audit.created_at AS last_action_at
                FROM product_reviews r
                JOIN products p ON p.id = r.product_id
                LEFT JOIN LATERAL (
                    SELECT action, actor_user_id, created_at FROM product_review_moderation_events
                    WHERE review_id = r.id ORDER BY created_at DESC, id DESC LIMIT 1
                ) audit ON TRUE
                LEFT JOIN app_users u ON u.id = audit.actor_user_id
                WHERE r.id = ?
                """, (rs, row) -> new ModerationView(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("product_name"), rs.getInt("rating"), rs.getString("review_text"),
                rs.getBoolean("is_published"), rs.getObject("customer_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getString("last_action"),
                rs.getString("last_actor_email"),
                rs.getTimestamp("last_action_at") == null ? null : rs.getTimestamp("last_action_at").toInstant()
        ), reviewId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Review not found");
        return rows.getFirst();
    }

    private static void validateFilters(Integer rating, int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException("rating must be between 1 and 5");
    }
}
