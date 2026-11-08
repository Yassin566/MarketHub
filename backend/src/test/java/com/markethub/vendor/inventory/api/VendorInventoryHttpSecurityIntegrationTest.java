package com.markethub.vendor.inventory.api;

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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VendorInventoryHttpSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private UUID vendorAUser;
    private UUID vendorAProduct;
    private UUID vendorBProduct;
    private String vendorAToken;

    @BeforeEach
    void setUp() {
        vendorAUser = createUser("inventory-a-" + UUID.randomUUID() + "@example.test");
        UUID vendorA = createVendor(vendorAUser, "Inventory Vendor A");
        UUID vendorB = createVendor(createUser("inventory-b-" + UUID.randomUUID() + "@example.test"),
                "Inventory Vendor B");
        vendorAProduct = createProduct(vendorA, "A stock item", "a-stock-" + UUID.randomUUID());
        vendorBProduct = createProduct(vendorB, "B stock item", "b-stock-" + UUID.randomUUID());
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, vendorAUser);
        vendorAToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(vendorAUser, email, "not-used", UserRole.VENDOR, true));
    }

    @Test
    void inventoryListIsTenantScoped() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/inventory")
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(vendorAProduct.toString())))
                .andExpect(content().string(not(containsString(vendorBProduct.toString()))));
    }

    @Test
    void adjustmentUpdatesQuantityAndWritesImmutableHistory() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/inventory/{productId}/adjustments", vendorAProduct)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta": 12, "reason": "RESTOCK", "expectedVersion": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"quantityAfter\":12")))
                .andExpect(content().string(containsString("\"versionAfter\":1")));

        org.junit.jupiter.api.Assertions.assertEquals(12L,
                jdbc.queryForObject("select on_hand_quantity from product_inventory where product_id=?",
                        Long.class, vendorAProduct));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("select count(*) from inventory_adjustments where product_id=?",
                        Integer.class, vendorAProduct));

        mockMvc.perform(get("/api/v1/vendor/inventory/{productId}/adjustments", vendorAProduct)
                        .header("Authorization", "Bearer " + vendorAToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RESTOCK")));
    }

    @Test
    void staleVersionCannotOverwriteNewerInventory() throws Exception {
        adjust(8, "RESTOCK", 0).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/vendor/inventory/{productId}/adjustments", vendorAProduct)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta": 3, "reason": "RESTOCK", "expectedVersion": 0}
                                """))
                .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertEquals(8L,
                jdbc.queryForObject("select on_hand_quantity from product_inventory where product_id=?",
                        Long.class, vendorAProduct));
    }

    @Test
    void stockCannotBeReducedBelowReservedQuantity() throws Exception {
        adjust(10, "RESTOCK", 0).andExpect(status().isOk());
        jdbc.update("update product_inventory set reserved_quantity=7 where product_id=?", vendorAProduct);

        mockMvc.perform(post("/api/v1/vendor/inventory/{productId}/adjustments", vendorAProduct)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta": -4, "reason": "DAMAGE", "expectedVersion": 1}
                                """))
                .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertEquals(10L,
                jdbc.queryForObject("select on_hand_quantity from product_inventory where product_id=?",
                        Long.class, vendorAProduct));
    }

    @Test
    void vendorCannotAdjustAnotherVendorsInventory() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/inventory/{productId}/adjustments", vendorBProduct)
                        .header("Authorization", "Bearer " + vendorAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta": 50, "reason": "RESTOCK", "expectedVersion": 0}
                                """))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("select on_hand_quantity from product_inventory where product_id=?",
                        Long.class, vendorBProduct));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbc.queryForObject("select count(*) from inventory_adjustments where product_id=?",
                        Integer.class, vendorBProduct));
    }

    private org.springframework.test.web.servlet.ResultActions adjust(long delta, String reason, long version)
            throws Exception {
        return mockMvc.perform(post("/api/v1/vendor/inventory/{productId}/adjustments", vendorAProduct)
                .header("Authorization", "Bearer " + vendorAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantityDelta": %d, "reason": "%s", "expectedVersion": %d}
                        """.formatted(delta, reason, version)));
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
        jdbc.update("insert into product_inventory(product_id,vendor_id) values (?,?)", id, vendorId);
        return id;
    }
}
