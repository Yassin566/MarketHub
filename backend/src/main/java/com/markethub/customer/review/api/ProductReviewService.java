package com.markethub.customer.review.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProductReviewService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REVIEW_LENGTH = 2000;
    private final JdbcTemplate jdbc;

    public ProductReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Review(UUID id, UUID productId, int rating,
                         String reviewText, Instant createdAt, Instant updatedAt) {}
    public record ReviewPage(List<Review> reviews, int limit, int offset, boolean hasMore,
                             BigDecimal averageRating, long reviewCount) {}

    @Transactional(readOnly = true)
    public ReviewPage listPublished(UUID productId, int limit, int offset) {
        validatePage(limit, offset);
        if (!productExists(productId)) throw new ResourceNotFoundException("Product not found");

        List<Review> rows = jdbc.query("""
                SELECT id, product_id, rating, review_text, created_at, updated_at
                FROM product_reviews
                WHERE product_id = ? AND is_published = TRUE
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new Review(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getInt("rating"),
                rs.getString("review_text"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), productId, limit + 1, offset);

        boolean hasMore = rows.size() > limit;
        List<Review> page = rows.stream().limit(limit).toList();
        ReviewStats stats = jdbc.queryForObject("""
                SELECT COALESCE(AVG(rating), 0)::numeric AS average_rating, COUNT(*) AS review_count
                FROM product_reviews WHERE product_id = ? AND is_published = TRUE
                """, (rs, row) -> new ReviewStats(rs.getBigDecimal("average_rating"),
                rs.getLong("review_count")), productId);
        return new ReviewPage(page, limit, offset, hasMore,
                stats.averageRating(), stats.reviewCount());
    }

    @Transactional
    public Review create(UUID customerId, UUID productId, int rating, String reviewText) {
        validateReview(rating, reviewText);
        if (!productExists(productId)) throw new ResourceNotFoundException("Product not found");

        List<UUID> eligibleOrders = jdbc.query("""
                SELECT co.id
                FROM customer_orders co
                JOIN order_items oi ON oi.order_id = co.id
                JOIN vendor_orders vo ON vo.id = oi.vendor_order_id
                WHERE co.customer_user_id = ? AND oi.product_id = ? AND vo.status = 'FULFILLED'
                ORDER BY co.created_at DESC, co.id DESC
                LIMIT 1
                """, (rs, row) -> rs.getObject("id", UUID.class), customerId, productId);
        if (eligibleOrders.isEmpty()) {
            throw new IllegalStateException("A fulfilled purchase of this product is required before reviewing it");
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO product_reviews
                    (id, product_id, customer_user_id, order_id, rating, review_text)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, productId, customerId, eligibleOrders.getFirst(), rating, normalizeText(reviewText));
        return loadOwned(customerId, id);
    }

    @Transactional
    public Review update(UUID customerId, UUID reviewId, int rating, String reviewText) {
        validateReview(rating, reviewText);
        int changed = jdbc.update("""
                UPDATE product_reviews
                SET rating = ?, review_text = ?, updated_at = now()
                WHERE id = ? AND customer_user_id = ?
                """, rating, normalizeText(reviewText), reviewId, customerId);
        if (changed == 0) throw new ResourceNotFoundException("Review not found");
        return loadOwned(customerId, reviewId);
    }

    @Transactional
    public void delete(UUID customerId, UUID reviewId) {
        int changed = jdbc.update("DELETE FROM product_reviews WHERE id = ? AND customer_user_id = ?",
                reviewId, customerId);
        if (changed == 0) throw new ResourceNotFoundException("Review not found");
    }

    private Review loadOwned(UUID customerId, UUID id) {
        List<Review> rows = jdbc.query("""
                SELECT id, product_id, rating, review_text, created_at, updated_at
                FROM product_reviews WHERE id = ? AND customer_user_id = ?
                """, (rs, row) -> new Review(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getInt("rating"),
                rs.getString("review_text"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), id, customerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Review not found");
        return rows.getFirst();
    }

    private boolean productExists(UUID productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM products WHERE id = ?)", Boolean.class, productId));
    }

    private static void validateReview(int rating, String reviewText) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("rating must be between 1 and 5");
        if (reviewText != null && reviewText.length() > MAX_REVIEW_LENGTH) {
            throw new IllegalArgumentException("reviewText must not exceed 2000 characters");
        }
    }

    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) return null;
        return text.trim();
    }

    private static void validatePage(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
    }

    private record ReviewStats(BigDecimal averageRating, long reviewCount) {}
}
