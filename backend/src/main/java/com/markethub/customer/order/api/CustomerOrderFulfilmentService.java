package com.markethub.customer.order.api;

import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerOrderFulfilmentService {
    private final JdbcTemplate jdbc;

    public CustomerOrderFulfilmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Shipment(UUID id, String carrier, String trackingNumber, String trackingUrl,
                           Instant shippedAt, LocalDate estimatedDeliveryDate) {}
    public record VendorFulfilment(UUID vendorOrderId, String vendorName, String status,
                                   Shipment shipment) {}

    @Transactional(readOnly = true)
    public List<VendorFulfilment> get(UUID customerId, UUID orderId) {
        Integer owned = jdbc.queryForObject("""
                SELECT count(*) FROM customer_orders
                WHERE id = ? AND customer_user_id = ?
                """, Integer.class, orderId, customerId);
        if (owned == null || owned == 0) throw new ResourceNotFoundException("Order not found");

        return jdbc.query("""
                SELECT vo.id AS vendor_order_id, v.display_name AS vendor_name, vo.status,
                       s.id AS shipment_id, s.carrier, s.tracking_number, s.tracking_url,
                       s.shipped_at, s.estimated_delivery_date
                FROM vendor_orders vo
                JOIN vendors v ON v.id = vo.vendor_id
                LEFT JOIN vendor_order_shipments s
                    ON s.vendor_order_id = vo.id AND s.vendor_id = vo.vendor_id
                WHERE vo.order_id = ?
                ORDER BY v.display_name, vo.id
                """, (rs, row) -> {
            Shipment shipment = rs.getObject("shipment_id") == null ? null : new Shipment(
                    rs.getObject("shipment_id", UUID.class), rs.getString("carrier"),
                    rs.getString("tracking_number"), rs.getString("tracking_url"),
                    rs.getTimestamp("shipped_at").toInstant(),
                    rs.getObject("estimated_delivery_date", LocalDate.class));
            return new VendorFulfilment(rs.getObject("vendor_order_id", UUID.class),
                    rs.getString("vendor_name"), rs.getString("status"), shipment);
        }, orderId);
    }
}
