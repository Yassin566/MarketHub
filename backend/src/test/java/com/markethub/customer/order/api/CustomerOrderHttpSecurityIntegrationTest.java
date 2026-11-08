package com.markethub.customer.order.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerOrderHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private UUID customerId;
    private UUID productId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("order-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID vendorUser = createUser("order-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        UUID vendorId = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                vendorId, vendorUser, "Order Vendor");
        productId = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,vendor_id,name,slug,price,currency,status)
                values (?,?,?, ?,12.5000,'USD','ACTIVE')
                """, productId, vendorId, "Order product", "order-product-" + UUID.randomUUID());
        jdbc.update("insert into product_inventory(product_id,vendor_id,on_hand_quantity) values (?,?,5)",
                productId, vendorId);
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, customerId);
        customerToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(customerId, email, "not-used", UserRole.CUSTOMER, true));
    }

    @Test
    void checkoutCreatesPendingOrderAndReservesStockAtomically() throws Exception {
        addToCart(2);
        UUID key = UUID.randomUUID();

        var result = mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"PENDING_PAYMENT\"")))
                .andExpect(content().string(containsString("\"lineTotal\":25.0000")))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select reserved_quantity from product_inventory where product_id=?",
                Long.class, productId)).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from customer_orders where customer_user_id=?",
                Long.class, customerId)).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from customer_cart_items ci join customer_carts c on c.id=ci.cart_id where c.customer_user_id=?",
                Long.class, customerId)).isZero();

        mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select reserved_quantity from product_inventory where product_id=?",
                Long.class, productId)).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from customer_orders where customer_user_id=?",
                Long.class, customerId)).isEqualTo(1L);
    }

    @Test
    void insufficientStockRollsBackOrderAndReservationWrites() throws Exception {
        jdbc.update("update product_inventory set reserved_quantity=4 where product_id=?", productId);
        addToCart(2);

        mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Insufficient available stock")));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from customer_orders where customer_user_id=?",
                Long.class, customerId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from inventory_reservations where customer_user_id=? and order_id is not null",
                Long.class, customerId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select reserved_quantity from product_inventory where product_id=?",
                Long.class, productId)).isEqualTo(4L);
    }

    @Test
    void anotherCustomerCannotReadTheOrder() throws Exception {
        addToCart(1);
        String response = mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");

        UUID otherCustomer = createUser("order-other-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, otherCustomer);
        String otherToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(otherCustomer, email, "not-used", UserRole.CUSTOMER, true));

        mockMvc.perform(get("/api/v1/customer/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyCustomersCanCreateOrders() throws Exception {
        UUID vendorUser = createUser("order-non-customer-" + UUID.randomUUID() + "@example.test", "VENDOR");
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, vendorUser);
        String token = jwtService.issueAccessToken(
                new MarketHubPrincipal(vendorUser, email, "not-used", UserRole.VENDOR, true));

        mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }
 
    @Test
    void customerCanPageThroughOnlyTheirOwnOrderHistory() throws Exception {
        addToCart(1);
        mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        addToCart(1);
        mockMvc.perform(post("/api/v1/customer/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customer/orders?limit=1&offset=0")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.orders[0].items.length()").value(1));

        mockMvc.perform(get("/api/v1/customer/orders?limit=1&offset=1")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void orderHistoryRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/customer/orders?limit=101")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/customer/orders?offset=-1")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest());
    }

    private void addToCart(long quantity) throws Exception {
        mockMvc.perform(put("/api/v1/customer/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":%d}
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }
}
