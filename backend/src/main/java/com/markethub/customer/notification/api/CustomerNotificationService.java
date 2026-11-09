package com.markethub.customer.notification.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerNotificationService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;

    public CustomerNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Notification(UUID id, UUID orderId, String type, String title,
                               String message, Instant createdAt, Instant readAt) {}
    public record NotificationPage(List<Notification> notifications, int limit, int offset,
                                   boolean hasMore, long unreadCount) {}

    @Transactional(readOnly = true)
    public NotificationPage list(UUID customerId, int limit, int offset, boolean unreadOnly) {
        validatePage(limit, offset);
        String filter = unreadOnly ? " AND read_at IS NULL" : "";
        List<UUID> ids = jdbc.query(
                "SELECT id FROM customer_notifications WHERE customer_user_id = ?" + filter
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> rs.getObject("id", UUID.class), customerId, limit + 1, offset);
        boolean hasMore = ids.size() > limit;
        List<Notification> notifications = ids.stream().limit(limit)
                .map(id -> load(customerId, id)).toList();
        long unreadCount = jdbc.queryForObject(
                "SELECT count(*) FROM customer_notifications WHERE customer_user_id = ? AND read_at IS NULL",
                Long.class, customerId);
        return new NotificationPage(notifications, limit, offset, hasMore, unreadCount);
    }

    @Transactional
    public Notification markRead(UUID customerId, UUID notificationId) {
        int updated = jdbc.update("""
                UPDATE customer_notifications SET read_at = now()
                WHERE id = ? AND customer_user_id = ? AND read_at IS NULL
                """, notificationId, customerId);
        if (updated == 0 && !exists(customerId, notificationId)) {
            throw new ResourceNotFoundException("Notification not found");
        }
        return load(customerId, notificationId);
    }

    @Transactional
    public long markAllRead(UUID customerId) {
        return jdbc.update("""
                UPDATE customer_notifications SET read_at = now()
                WHERE customer_user_id = ? AND read_at IS NULL
                """, customerId);
    }

    /**
     * Creates one inbox item per business event. The unique customer/event key
     * makes retries safe when an upstream payment or fulfilment event is replayed.
     */
    @Transactional
    public Notification create(UUID customerId, UUID orderId, String eventKey,
                               String type, String title, String message) {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 160) {
            throw new IllegalArgumentException("eventKey must contain 1 to 160 characters");
        }
        if (type == null || type.isBlank() || type.length() > 40) {
            throw new IllegalArgumentException("type must contain 1 to 40 characters");
        }
        if (title == null || title.isBlank() || title.length() > 160) {
            throw new IllegalArgumentException("title must contain 1 to 160 characters");
        }
        if (message == null || message.isBlank() || message.length() > 500) {
            throw new IllegalArgumentException("message must contain 1 to 500 characters");
        }
        UUID id = jdbc.queryForObject("""
                INSERT INTO customer_notifications
                    (id, customer_user_id, order_id, event_key, type, title, message)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (customer_user_id, event_key)
                DO UPDATE SET event_key = EXCLUDED.event_key
                RETURNING id
                """, UUID.class, UUID.randomUUID(), customerId, orderId,
                eventKey, type, title, message);
        return load(customerId, id);
    }

    private boolean exists(UUID customerId, UUID notificationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM customer_notifications
                              WHERE id = ? AND customer_user_id = ?)
                """, Boolean.class, notificationId, customerId));
    }

    private Notification load(UUID customerId, UUID id) {
        List<Notification> rows = jdbc.query("""
                SELECT id, order_id, type, title, message, created_at, read_at
                FROM customer_notifications
                WHERE id = ? AND customer_user_id = ?
                """, (rs, row) -> new Notification(
                rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getString("type"), rs.getString("title"), rs.getString("message"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                id, customerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Notification not found");
        return rows.getFirst();
    }

    private static void validatePage(int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
    }
}
