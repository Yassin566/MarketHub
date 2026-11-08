package com.markethub.vendor.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.markethub.identity.domain.UserRole;
import com.markethub.identity.security.JwtService;
import com.markethub.identity.security.MarketHubPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real JWT filter, Spring Security chain, MVC controllers and PostgreSQL queries.
 * Fixtures are rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VendorProductHttpSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private UUID vendorAUser;
    private UUID vendorBUser;
    private UUID vendorAProduct;
    private UUID vendorBProduct;
    private String vendorAToken;

    @BeforeEach
    void setUp() {
        vendorAUser = createUser("vendor-a-" + UUID.randomUUID() + "@example.test");
        vendorBUser = createUser("vendor-b-" + UUID.randomUUID() + "@example.test");
        UUID vendorA = createVendor(vendorAUser, "Vendor A");
        UUID vendorB = createVendor(vendorBUser, "Vendor B");
        vendorAProduct = createProduct(vendorA, "Vendor A product", "vendor-a-" + UUID.randomUUID());
        vendorBProduct = createProduct(vendorB, "Vendor B product", "vendor-b-" + UUID.randomUUID());

        vendorAToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(vendorAUser, "vendor-a-" + vendorAUser + "@example.test",
                        "not-used", UserRole.VENDOR, true));
        // The persisted email must match the JWT email claim; issue a token using the stored identity.
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, vendorAUser);
        vendorAToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(vendorAUser, email, "not-used", UserRole.VENDOR, true));
    }

    @Test
    void vendorProductListContainsOnlyTheAuthenticatedVendorsProducts() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/products")
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(vendorAProduct.toString())))
                .andExpect(content().string(not(containsString(vendorBProduct.toString()))));
    }

    @Test
    void vendorCannotUpdateAnotherVendorsProductEvenWhenItKnowsTheUuid() throws Exception {
        String body = """
                {
                  "name": "Hijacked product",
                  "slug": "hijacked-product",
                  "description": "must not be applied",
                  "price": 1.0000,
                  "currency": "USD",
                  "expectedVersion": 0
                }
                """;

        mockMvc.perform(put("/api/v1/vendor/products/{productId}", vendorBProduct)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals(
                "Vendor B product",
                jdbc.queryForObject("select name from products where id=?", String.class, vendorBProduct));
    }

    @Test
    void vendorCannotArchiveAnotherVendorsProductEvenWhenItKnowsTheUuid() throws Exception {
        mockMvc.perform(delete("/api/v1/vendor/products/{productId}", vendorBProduct)
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals(
                "ACTIVE",
                jdbc.queryForObject("select status from products where id=?", String.class, vendorBProduct));
    }

    private UUID createUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,'VENDOR','ACTIVE')",
                id, email, "integration-test-hash");
        return id;
    }

    private UUID createVendor(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                id, ownerId, name);
        return id;
    }

    private UUID createProduct(UUID vendorId, String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into products(id,vendor_id,name,slug,price,currency,status) values (?,?,?,?,1.0000,'USD','ACTIVE')",
                id, vendorId, name, slug);
        return id;
    }
}
