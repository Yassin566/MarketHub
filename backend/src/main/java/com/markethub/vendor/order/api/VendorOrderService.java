package com.markethub.vendor.order.api;

import com.markethub.common.exception.ResourceNotFoundException;
import com.markethub.vendor.application.ApprovedVendorAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class VendorOrderService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final ApprovedVendorAccess access;
    public VendorOrderService(JdbcTemplate jdbc, ApprovedVendorAccess access) { this.jdbc = jdbc; this.access = access; }

    public record OrderLine(UUID id, UUID productId, String productName, long quantity, BigDecimal unitPrice, String currency, BigDecimal lineTotal) {}
    public record StatusChange(UUID id, String previousStatus, String newStatus, String note, Instant createdAt) {}
    public record Shipment(UUID id, String carrier, String trackingNumber, String trackingUrl, Instant shippedAt, LocalDate estimatedDeliveryDate) {}
    public record OrderView(UUID id, UUID customerOrderId, String status, String currency, BigDecimal subtotal, Instant createdAt, List<OrderLine> items, List<StatusChange> statusHistory, Shipment shipment) {}
    public record OrderPage(List<OrderView> orders, int limit, int offset, boolean hasMore) {}
    private record CurrentOrder(String status) {}

    @Transactional(readOnly = true)
    public OrderPage list(UUID userId, int limit, int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        UUID vendorId = access.requireApprovedVendor(userId);
        List<UUID> ids = jdbc.query("SELECT id FROM vendor_orders WHERE vendor_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,row)->rs.getObject("id",UUID.class),vendorId,limit+1,offset);
        boolean more=ids.size()>limit;
        return new OrderPage(ids.stream().limit(limit).map(id->load(vendorId,id)).toList(),limit,offset,more);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID userId, UUID orderId) { return load(access.requireApprovedVendor(userId),orderId); }

    @Transactional
    public OrderView updateStatus(UUID userId, UUID orderId, String requested, String note) {
        UUID vendorId=access.requireApprovedVendor(userId);
        String current=lockOrder(vendorId,orderId);
        if (!("PAID".equals(current)&&"PROCESSING".equals(requested)))
            throw new IllegalStateException("Only PAID to PROCESSING is allowed here; use shipment dispatch to complete fulfilment");
        transition(orderId,vendorId,userId,current,requested,note);
        return load(vendorId,orderId);
    }

    @Transactional
    public OrderView dispatch(UUID userId, UUID orderId, VendorShipmentRequest request) {
        UUID vendorId=access.requireApprovedVendor(userId);
        String current=lockOrder(vendorId,orderId);
        if (!"PROCESSING".equals(current)) throw new IllegalStateException("Only PROCESSING vendor orders can be dispatched");
        String trackingUrl=normalizeAndValidateTrackingUrl(request.trackingUrl());
        jdbc.update("""
            INSERT INTO vendor_order_shipments
              (id,vendor_order_id,vendor_id,carrier,tracking_number,tracking_url,estimated_delivery_date,created_by)
            VALUES (?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),orderId,vendorId,request.carrier().trim(),request.trackingNumber().trim(),
                trackingUrl,request.estimatedDeliveryDate(),userId);
        transition(orderId,vendorId,userId,current,"FULFILLED",
                "Dispatched with "+request.carrier().trim()+" (tracking "+request.trackingNumber().trim()+")");
        return load(vendorId,orderId);
    }

    private String lockOrder(UUID vendorId, UUID orderId) {
        List<CurrentOrder> rows=jdbc.query("SELECT status FROM vendor_orders WHERE id=? AND vendor_id=? FOR UPDATE",
                (rs,row)->new CurrentOrder(rs.getString("status")),orderId,vendorId);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Vendor order not found");
        return rows.getFirst().status();
    }

    private String normalizeAndValidateTrackingUrl(String value) {
        if(value==null||value.isBlank()) return null;
        try {
            URI uri=URI.create(value.trim()); String scheme=uri.getScheme();
            if(uri.getHost()==null||scheme==null||!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)))
                throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch(IllegalArgumentException ex) {
            throw new IllegalArgumentException("trackingUrl must be an absolute HTTP or HTTPS URL");
        }
    }

    private void transition(UUID orderId, UUID vendorId, UUID actorId, String previous, String next, String note) {
        int changed=jdbc.update("UPDATE vendor_orders SET status=?,updated_at=now() WHERE id=? AND vendor_id=? AND status=?",
                next,orderId,vendorId,previous);
        if(changed!=1) throw new IllegalStateException("Vendor order changed concurrently; reload and retry");
        jdbc.update("""
            INSERT INTO vendor_order_status_history(id,vendor_order_id,vendor_id,actor_user_id,previous_status,new_status,note)
            VALUES (?,?,?,?,?,?,?)
            """,UUID.randomUUID(),orderId,vendorId,actorId,previous,next,note.trim());
        jdbc.update("""
            INSERT INTO customer_notifications
                (id, customer_user_id, order_id, event_key, type, title, message)
            SELECT ?, co.customer_user_id, vo.order_id, ?, 'ORDER',
                   CASE WHEN ? = 'PROCESSING' THEN 'Your order is being prepared' ELSE 'Your order has shipped' END,
                   CASE WHEN ? = 'PROCESSING'
                        THEN 'The vendor has started preparing your order.'
                        ELSE 'The vendor has dispatched your order. Open order details to view tracking information.' END
            FROM vendor_orders vo
            JOIN customer_orders co ON co.id = vo.order_id
            WHERE vo.id = ? AND vo.vendor_id = ?
            ON CONFLICT (customer_user_id, event_key) DO NOTHING
            """, UUID.randomUUID(), "vendor-order:" + orderId + ":status:" + next,
                next, next, orderId, vendorId);
    }

    private OrderView load(UUID vendorId, UUID orderId) {
        List<OrderView> rows=jdbc.query("""
            SELECT vo.id,vo.order_id,vo.status,vo.currency,vo.subtotal,vo.created_at,
                   s.id AS shipment_id,s.carrier,s.tracking_number,s.tracking_url,s.shipped_at,s.estimated_delivery_date
            FROM vendor_orders vo LEFT JOIN vendor_order_shipments s
              ON s.vendor_order_id=vo.id AND s.vendor_id=vo.vendor_id
            WHERE vo.id=? AND vo.vendor_id=?
            """,(rs,row)->{
                Shipment shipment=rs.getObject("shipment_id")==null?null:new Shipment(
                    rs.getObject("shipment_id",UUID.class),rs.getString("carrier"),rs.getString("tracking_number"),
                    rs.getString("tracking_url"),rs.getTimestamp("shipped_at").toInstant(),
                    rs.getObject("estimated_delivery_date",LocalDate.class));
                return new OrderView(rs.getObject("id",UUID.class),rs.getObject("order_id",UUID.class),
                    rs.getString("status"),rs.getString("currency"),rs.getBigDecimal("subtotal"),
                    rs.getTimestamp("created_at").toInstant(),List.of(),List.of(),shipment);
            },orderId,vendorId);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Vendor order not found");
        OrderView h=rows.getFirst();
        List<OrderLine> items=jdbc.query("""
            SELECT id,product_id,product_name,quantity,unit_price,currency,line_total
            FROM order_items WHERE vendor_order_id=? AND vendor_id=? ORDER BY id
            """,(rs,row)->new OrderLine(rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),
                rs.getString("product_name"),rs.getLong("quantity"),rs.getBigDecimal("unit_price"),
                rs.getString("currency"),rs.getBigDecimal("line_total")),orderId,vendorId);
        List<StatusChange> history=jdbc.query("""
            SELECT id,previous_status,new_status,note,created_at FROM vendor_order_status_history
            WHERE vendor_order_id=? AND vendor_id=? ORDER BY created_at DESC,id DESC
            """,(rs,row)->new StatusChange(rs.getObject("id",UUID.class),rs.getString("previous_status"),
                rs.getString("new_status"),rs.getString("note"),rs.getTimestamp("created_at").toInstant()),orderId,vendorId);
        return new OrderView(h.id(),h.customerOrderId(),h.status(),h.currency(),h.subtotal(),h.createdAt(),
            List.copyOf(items),List.copyOf(history),h.shipment());
    }
}
