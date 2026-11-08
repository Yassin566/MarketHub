package com.markethub.customer.payment.api;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerPaymentHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private PaymentRequestedEventHandler paymentRequestedHandler;

    private UUID customerId;
    private UUID productId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("payment-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID vendorUser = createUser("payment-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        UUID vendorId = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                vendorId, vendorUser, "Payment Vendor");
        productId = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,vendor_id,name,slug,price,currency,status)
                values (?,?,?, ?,12.5000,'USD','ACTIVE')
                """, productId, vendorId, "Payment product", "payment-product-" + UUID.randomUUID());
        jdbc.update("insert into product_inventory(product_id,vendor_id,on_hand_quantity) values (?,?,5)",
                productId, vendorId);
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, customerId);
        customerToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(customerId, email, "not-used", UserRole.CUSTOMER, true));
    }

    @Test
    void paymentRequestAndOutboxAreAtomicAndIdempotent() throws Exception {
        UUID orderId = createOrder();
        UUID key = UUID.randomUUID();

        var first = mockMvc.perform(post("/api/v1/customer/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.providerCode").value("UNCONFIGURED"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/customer/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from payments where order_id=?", Long.class, orderId)).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select amount from payments where order_id=?", java.math.BigDecimal.class, orderId))
                .isEqualByComparingTo("12.5000");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from transactional_outbox where aggregate_id=?",
                Long.class, UUID.fromString(paymentId))).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from customer_orders where id=?", String.class, orderId))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void sandboxProviderDispatchIsIdempotentAndNeverMarksOrderPaid() throws Exception {
        UUID orderId = createOrder();
        UUID key = UUID.randomUUID();
        String response = mockMvc.perform(post("/api/v1/customer/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(response, "$.data.id"));

        String payload = """
                {"paymentId":"%s","orderId":"%s","amount":"12.5000","currency":"USD"}
                """.formatted(paymentId, orderId);
        var event = new TransactionalOutboxService.OutboxMessage(
                UUID.randomUUID(), "PAYMENT_REQUESTED", payload, 1, UUID.randomUUID());

        paymentRequestedHandler.handle(event);
        String firstReference = jdbc.queryForObject(
                "select provider_reference from payments where id=?", String.class, paymentId);

        paymentRequestedHandler.handle(event);
        String secondReference = jdbc.queryForObject(
                "select provider_reference from payments where id=?", String.class, paymentId);

        org.assertj.core.api.Assertions.assertThat(firstReference)
                .startsWith("sandbox-")
                .isEqualTo(secondReference);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select provider_code from payments where id=?", String.class, paymentId))
                .isEqualTo("SANDBOX");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from payments where id=?", String.class, paymentId))
                .isEqualTo("PROCESSING");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from customer_orders where id=?", String.class, orderId))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void paymentCannotStartAfterReservationExpires() throws Exception {
        UUID orderId = createOrder();
        jdbc.update("update inventory_reservations set expires_at=now() - interval '1 minute' where order_id=?",
                orderId);

        mockMvc.perform(post("/api/v1/customer/orders/{orderId}/payments", orderId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("reservations are no longer valid")));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from payments where order_id=?", Long.class, orderId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from transactional_outbox", Long.class)).isZero();
    }

    private UUID createOrder() throws Exception {
        mockMvc.perform(put("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":1}
                                """.formatted(productId)))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.data.id"));
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }
}
