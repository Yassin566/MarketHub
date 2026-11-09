package com.markethub.vendor.order.api;

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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VendorOrderHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private UUID vendorAUser;
    private UUID vendorAOrder;
    private UUID vendorBOrder;
    private String vendorAToken;

    @BeforeEach
    void setUp() {
        vendorAUser = createUser("vendor-order-a-" + UUID.randomUUID() + "@example.test", "VENDOR");
        UUID vendorA = createVendor(vendorAUser, "Vendor A");
        UUID vendorB = createVendor(createUser("vendor-order-b-" + UUID.randomUUID() + "@example.test", "VENDOR"), "Vendor B");
        vendorAOrder = createOrder(vendorA, "PAID");
        vendorBOrder = createOrder(vendorB, "PAID");
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, vendorAUser);
        vendorAToken = token(vendorAUser, email, UserRole.VENDOR);
    }

    @Test
    void listAndDetailExposeOnlyAuthenticatedVendorsOrders() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(vendorAOrder.toString())))
                .andExpect(content().string(not(containsString(vendorBOrder.toString()))));

        mockMvc.perform(get("/api/v1/vendor/orders/{id}", vendorBOrder)
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusFilterReturnsOnlyMatchingOrdersWithinVendorScope() throws Exception {
        UUID processingOrder = createOrder(jdbc.queryForObject(
                "select id from vendors where owner_user_id=?", UUID.class, vendorAUser), "PROCESSING");
        mockMvc.perform(get("/api/v1/vendor/orders").queryParam("status", "PROCESSING")
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.orders[0].id").value(processingOrder.toString()))
                .andExpect(jsonPath("$.data.orders[0].status").value("PROCESSING"));
        mockMvc.perform(get("/api/v1/vendor/orders").queryParam("status", "UNKNOWN")
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vendorCanAdvancePaidOrderAndTransitionIsAudited() throws Exception {
        mockMvc.perform(patch("/api/v1/vendor/orders/{id}/status", vendorAOrder)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PROCESSING","note":"Preparing items"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.data.statusHistory[0].previousStatus").value("PAID"))
                .andExpect(jsonPath("$.data.statusHistory[0].newStatus").value("PROCESSING"));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from vendor_orders where id=?", String.class, vendorAOrder)).isEqualTo("PROCESSING");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from customer_orders where id=(select order_id from vendor_orders where id=?)",
                String.class, vendorAOrder)).isEqualTo("PENDING_PAYMENT");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from vendor_order_status_history where vendor_order_id=? and actor_user_id=?",
                Integer.class, vendorAOrder, vendorAUser)).isEqualTo(1);
    }

    @Test
    void invalidOrRepeatedTransitionConflictsWithoutAudit() throws Exception {
        mockMvc.perform(patch("/api/v1/vendor/orders/{id}/status", vendorAOrder)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FULFILLED","note":"Skip preparation"}
                                """))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from vendor_order_status_history where vendor_order_id=?",
                Integer.class, vendorAOrder)).isZero();
    }

    @Test
    void customerCannotUseVendorOrderEndpoints() throws Exception {
        UUID customer = createUser("vendor-order-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, customer);
        String customerToken = token(customer, email, UserRole.CUSTOMER);
        mockMvc.perform(get("/api/v1/vendor/orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }

    private UUID createVendor(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                id, owner, name);
        return id;
    }

    private UUID createOrder(UUID vendor, String status) {
        UUID customer = createUser("order-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                insert into customer_orders(id,customer_user_id,idempotency_key,status,currency,subtotal,total)
                values (?,?,?,'PENDING_PAYMENT','USD',10.0000,10.0000)
                """, orderId, customer, UUID.randomUUID());
        UUID vendorOrderId = UUID.randomUUID();
        jdbc.update("""
                insert into vendor_orders(id,order_id,vendor_id,status,currency,subtotal)
                values (?,?,?,?, 'USD',10.0000)
                """, vendorOrderId, orderId, vendor, status);
        UUID product = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,vendor_id,name,slug,price,currency,status)
                values (?,?,? ,?,10.0000,'USD','ACTIVE')
                """, product, vendor, "Order product", "order-product-" + UUID.randomUUID());
        jdbc.update("""
                insert into order_items(id,order_id,vendor_order_id,product_id,vendor_id,product_name,
                    quantity,unit_price,currency,line_total)
                values (?,?,?,?,?, 'Order product',1,10.0000,'USD',10.0000)
                """, UUID.randomUUID(), orderId, vendorOrderId, product, vendor);
        return vendorOrderId;
    }

    private String token(UUID userId, String email, UserRole role) {
        return jwtService.issueAccessToken(new MarketHubPrincipal(userId, email, "not-used", role, true));
    }
}
