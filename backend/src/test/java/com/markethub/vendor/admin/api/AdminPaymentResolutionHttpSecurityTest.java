package com.markethub.vendor.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.markethub.common.outbox.TransactionalOutboxService;
import com.markethub.customer.payment.provider.PaymentRequestedEventHandler;
import com.markethub.identity.domain.UserRole;
import com.markethub.identity.security.JwtService;
import com.markethub.identity.security.MarketHubPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPaymentResolutionHttpSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private PaymentRequestedEventHandler paymentRequestedHandler;

    private UUID customerId;
    private UUID productId;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("resolution-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID adminId = createUser("resolution-admin-" + UUID.randomUUID() + "@example.test", "ADMIN");
        UUID vendorUserId = createUser("resolution-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        UUID vendorId = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                vendorId, vendorUserId, "Resolution Vendor");
        productId = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,vendor_id,name,slug,price,currency,status)
                values (?,?,?, ?,12.5000,'USD','ACTIVE')
                """, productId, vendorId, "Resolution product", "resolution-product-" + UUID.randomUUID());
        jdbc.update("insert into product_inventory(product_id,vendor_id,on_hand_quantity) values (?,?,5)",
                productId, vendorId);

        String customerEmail = jdbc.queryForObject(
                "select email from app_users where id=?", String.class, customerId);
        String adminEmail = jdbc.queryForObject(
                "select email from app_users where id=?", String.class, adminId);
        customerToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(customerId, customerEmail, "unused", UserRole.CUSTOMER, true));
        adminToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(adminId, adminEmail, "unused", UserRole.ADMIN, true));
    }

    @Test
    void confirmedSuccessConsumesOnlyValidReservationsAndWritesActorAudit() throws Exception {
        Payment payment = createProcessingPayment();
        jdbc.update("update payments set status='REQUIRES_REVIEW' where id=?", payment.id());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/resolution", payment.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_SUCCEEDED",
                                 "evidenceReference":"provider-txn-8472",
                                 "reason":"Provider dashboard confirms captured payment"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultingPaymentStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.actorEmail").value(org.hamcrest.Matchers.containsString("resolution-admin-")));

        assertThat(jdbc.queryForObject("select status from payments where id=?", String.class, payment.id()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select status from customer_orders where id=?", String.class, payment.orderId()))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select status from inventory_reservations where order_id=?",
                String.class, payment.orderId())).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("select on_hand_quantity from product_inventory where product_id=?",
                Long.class, productId)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("select reserved_quantity from product_inventory where product_id=?",
                Long.class, productId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payment_manual_resolutions where payment_id=?",
                Long.class, payment.id())).isEqualTo(1L);
        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/resolution", payment.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_SUCCEEDED","evidenceReference":"provider-txn-8472","reason":"duplicate"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmedFailureReleasesReservationsAndCancelsPendingOrder() throws Exception {
        Payment payment = createProcessingPayment();
        jdbc.update("update payments set status='REQUIRES_REVIEW' where id=?", payment.id());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/resolution", payment.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_FAILED",
                                 "evidenceReference":"provider-case-991",
                                 "reason":"Provider confirms authorization was declined"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultingPaymentStatus").value("FAILED"));

        assertThat(jdbc.queryForObject("select status from payments where id=?", String.class, payment.id()))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from customer_orders where id=?", String.class, payment.orderId()))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from inventory_reservations where order_id=?",
                String.class, payment.orderId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select reserved_quantity from product_inventory where product_id=?",
                Long.class, productId)).isZero();
    }

    @Test
    void successIsRejectedWhenReservationHasExpiredAndCustomerCannotResolve() throws Exception {
        Payment payment = createProcessingPayment();
        jdbc.update("update payments set status='REQUIRES_REVIEW' where id=?", payment.id());
        jdbc.update("update inventory_reservations set expires_at=now() - interval '1 minute' where order_id=?",
                payment.orderId());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/resolution", payment.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_SUCCEEDED",
                                 "evidenceReference":"provider-txn-late",
                                 "reason":"Provider confirms late capture"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/resolution", payment.id())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRM_FAILED","evidenceReference":"case","reason":"not authorized"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select status from payments where id=?", String.class, payment.id()))
                .isEqualTo("REQUIRES_REVIEW");
        assertThat(jdbc.queryForObject("select count(*) from payment_manual_resolutions where payment_id=?",
                Long.class, payment.id())).isZero();
        assertThat(jdbc.queryForObject("select on_hand_quantity from product_inventory where product_id=?",
                Long.class, productId)).isEqualTo(5L);
    }

    private Payment createProcessingPayment() throws Exception {
        mockMvc.perform(put("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":1}
                                """.formatted(productId)))
                .andExpect(status().isOk());

        String orderBody = mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(orderBody, "$.data.id"));

        String paymentBody = mockMvc.perform(post("/api/v1/customer/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(JsonPath.read(paymentBody, "$.data.id"));

        String payload = """
                {"paymentId":"%s","orderId":"%s","amount":"12.5000","currency":"USD"}
                """.formatted(paymentId, orderId);
        paymentRequestedHandler.handle(new TransactionalOutboxService.OutboxMessage(
                UUID.randomUUID(), "PAYMENT_REQUESTED", payload, 1, UUID.randomUUID()));
        return new Payment(paymentId, orderId);
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }

    private record Payment(UUID id, UUID orderId) {}
}
