package com.markethub.customer.cart.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerCartHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private UUID customerId;
    private UUID productId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("cart-owner-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID vendorUser = createUser("cart-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        UUID vendorId = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                vendorId, vendorUser, "Cart Vendor");
        productId = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,vendor_id,name,slug,price,currency,status)
                values (?,?,?, ?,12.5000,'USD','ACTIVE')
                """, productId, vendorId, "Cart product", "cart-product-" + UUID.randomUUID());
        jdbc.update("insert into product_inventory(product_id,vendor_id,on_hand_quantity) values (?,?,5)",
                productId, vendorId);
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, customerId);
        customerToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(customerId, email, "not-used", UserRole.CUSTOMER, true));
    }

    @Test
    void customerCanPersistReplaceAndRemoveCartItems() throws Exception {
        putItem(2).andExpect(status().isOk())
                .andExpect(content().string(containsString("\"quantity\":2")));
        putItem(3).andExpect(status().isOk())
                .andExpect(content().string(containsString("\"quantity\":3")));

        mockMvc.perform(get("/api/v1/customer/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"productName\":\"Cart product\"")))
                .andExpect(content().string(containsString("\"quantity\":3")));

        mockMvc.perform(delete("/api/v1/customer/cart/items/{productId}", productId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"items\":[]")));
    }

    @Test
    void checkoutValidationUsesCurrentPriceAndAvailableStock() throws Exception {
        putItem(2).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customer/cart/checkout/validate")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"valid\":true")))
                .andExpect(content().string(containsString("\"unitPrice\":12.5000")));

        jdbc.update("update product_inventory set reserved_quantity=4 where product_id=?", productId);
        mockMvc.perform(post("/api/v1/customer/cart/checkout/validate")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"valid\":false")))
                .andExpect(content().string(containsString("Insufficient available stock")));
    }

    @Test
    void cartIsIsolatedBetweenCustomers() throws Exception {
        putItem(1).andExpect(status().isOk());
        UUID otherCustomer = createUser("cart-other-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, otherCustomer);
        String otherToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(otherCustomer, email, "not-used", UserRole.CUSTOMER, true));

        mockMvc.perform(get("/api/v1/customer/cart")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"items\":[]")));
    }

    @Test
    void onlyCustomersCanAccessCartEndpoints() throws Exception {
        UUID vendorUser = createUser("cart-non-customer-" + UUID.randomUUID() + "@example.test", "VENDOR");
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, vendorUser);
        String token = jwtService.issueAccessToken(
                new MarketHubPrincipal(vendorUser, email, "not-used", UserRole.VENDOR, true));

        mockMvc.perform(get("/api/v1/customer/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAddInactiveOrUnapprovedProduct() throws Exception {
        jdbc.update("update products set status='ARCHIVED' where id=?", productId);
        putItem(1).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions putItem(long quantity) throws Exception {
        return mockMvc.perform(put("/api/v1/customer/cart/items")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productId":"%s","quantity":%d}
                        """.formatted(productId, quantity)));
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }
}
