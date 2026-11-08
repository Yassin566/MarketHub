package com.markethub.vendor.analytics.api;

import com.markethub.vendor.application.ApprovedVendorAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class VendorAnalyticsService {
    private static final int MAX_RANGE_DAYS = 366;
    private final JdbcTemplate jdbc;
    private final ApprovedVendorAccess access;

    public VendorAnalyticsService(JdbcTemplate jdbc, ApprovedVendorAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record CurrencySummary(String currency, long orderCount, long paidOrderCount,
                                  long processingOrderCount, long fulfilledOrderCount,
                                  long cancelledOrderCount, BigDecimal recognizedRevenue) {}
    public record DailyMetric(LocalDate date, String currency, long orderCount, BigDecimal recognizedRevenue) {}
    public record ProductMetric(UUID productId, String productName, String currency,
                                long unitsSold, BigDecimal recognizedRevenue) {}
    public record Analytics(LocalDate from, LocalDate to, List<CurrencySummary> currencies,
                            List<DailyMetric> daily, List<ProductMetric> topProducts) {}

    @Transactional(readOnly = true)
    public Analytics report(UUID userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("A valid from/to date range is required");
        if (from.plusDays(MAX_RANGE_DAYS - 1L).isBefore(to))
            throw new IllegalArgumentException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");

        UUID vendorId = access.requireApprovedVendor(userId);
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Timestamp startAt = Timestamp.from(start);
        Timestamp endAt = Timestamp.from(end);

        List<CurrencySummary> currencies = jdbc.query("""
            SELECT currency, COUNT(*) AS order_count,
                   COUNT(*) FILTER (WHERE status IN ('PAID','PROCESSING','FULFILLED')) AS paid_count,
                   COUNT(*) FILTER (WHERE status='PROCESSING') AS processing_count,
                   COUNT(*) FILTER (WHERE status='FULFILLED') AS fulfilled_count,
                   COUNT(*) FILTER (WHERE status='CANCELLED') AS cancelled_count,
                   COALESCE(SUM(subtotal) FILTER (WHERE status IN ('PAID','PROCESSING','FULFILLED')),0) AS revenue
            FROM vendor_orders
            WHERE vendor_id=? AND created_at>=? AND created_at<?
            GROUP BY currency ORDER BY currency
            """, (rs,n) -> new CurrencySummary(rs.getString("currency"), rs.getLong("order_count"),
                rs.getLong("paid_count"), rs.getLong("processing_count"), rs.getLong("fulfilled_count"),
                rs.getLong("cancelled_count"), rs.getBigDecimal("revenue")), vendorId, startAt, endAt);

        List<DailyMetric> daily = jdbc.query("""
            SELECT (created_at AT TIME ZONE 'UTC')::date AS metric_date, currency, COUNT(*) AS order_count,
                   COALESCE(SUM(subtotal) FILTER (WHERE status IN ('PAID','PROCESSING','FULFILLED')),0) AS revenue
            FROM vendor_orders
            WHERE vendor_id=? AND created_at>=? AND created_at<?
            GROUP BY (created_at AT TIME ZONE 'UTC')::date, currency
            ORDER BY (created_at AT TIME ZONE 'UTC')::date, currency
            """, (rs,n) -> new DailyMetric(rs.getTimestamp("utc_created_at").toInstant()
                .atZone(ZoneOffset.UTC).toLocalDate(), rs.getString("currency"),
                rs.getLong("order_count"), rs.getBigDecimal("revenue")), vendorId, startAt, endAt);

        List<ProductMetric> topProducts = jdbc.query("""
            SELECT oi.product_id, oi.product_name, oi.currency,
                   SUM(oi.quantity) AS units_sold,
                   SUM(oi.line_total) AS revenue
            FROM order_items oi
            JOIN vendor_orders vo ON vo.id=oi.vendor_order_id AND vo.vendor_id=oi.vendor_id
            WHERE vo.vendor_id=? AND vo.created_at>=? AND vo.created_at<?
              AND vo.status IN ('PAID','PROCESSING','FULFILLED')
            GROUP BY oi.product_id, oi.product_name, oi.currency
            ORDER BY revenue DESC, units_sold DESC, oi.product_name
            LIMIT 10
            """, (rs,n) -> new ProductMetric(rs.getObject("product_id", UUID.class),
                rs.getString("product_name"), rs.getString("currency"),
                rs.getLong("units_sold"), rs.getBigDecimal("revenue")), vendorId, startAt, endAt);

        return new Analytics(from, to, List.copyOf(currencies), List.copyOf(daily), List.copyOf(topProducts));
    }
}
