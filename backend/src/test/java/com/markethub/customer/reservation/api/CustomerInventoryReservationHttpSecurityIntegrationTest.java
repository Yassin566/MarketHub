package com.markethub.customer.reservation.api;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerInventoryReservationHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private UUID customerId;
    private UUID productId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("cart-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID vendorUser = createUser("cart-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        UUID vendorId = UUID.randomUUID();
        jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",
                vendorId, vendorUser, "Reservation Vendor");
        productId = UUID.randomUUID();
        jdbc.update("""
                insert into products(id,vendor_id,name,slug,price,currency,status)
                values (?,?,? ,?,1.0000,'USD','ACTIVE')
                """, productId, vendorId, "Reservable item", "reservable-" + UUID.randomUUID());
        jdbc.update("insert into product_inventory(product_id,vendor_id,on_hand_quantity) values (?,?,5)",
                productId, vendorId);
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, customerId);
        customerToken = jwtService.issueAccessToken(
                new MarketHubPrincipal(customerId, email, "not-used", UserRole.CUSTOMER, true));
    }

    @Test
    void reservationAtomicallyReducesAvailableStockAndCanBeReleased() throws Exception {
        String response = mockMvc.perform(post("/api/v1/customer/reservations")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":3}
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"quantity\":3")))
                .andExpect(content().string(containsString("\"status\":\"ACTIVE\"")))
                .andReturn().getResponse().getContentAsString();

        UUID reservationId = UUID.fromString(response.split("\"id\":\"")[1].split("\"")[0]);
        org.junit.jupiter.api.Assertions.assertEquals(3L,
                jdbc.queryForObject("select reserved_quantity from product_inventory where product_id=?",
                        Long.class, productId));

        mockMvc.perform(delete("/api/v1/customer/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"RELEASED\"")));

        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("select reserved_quantity from product_inventory where product_id=?",
                        Long.class, productId));
    }

    @Test
    void cannotReserveMoreThanAvailableStock() throws Exception {
        mockMvc.perform(post("/api/v1/customer/reservations")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":6}
                                """.formatted(productId)))
                .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("select reserved_quantity from product_inventory where product_id=?",
                        Long.class, productId));
    }

    @Test
    void expiredReservationsReleaseStockDuringCustomerRead() throws Exception {
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                insert into inventory_reservations
                    (id,customer_user_id,product_id,vendor_id,quantity,status,expires_at)
                values (?, ?, ?, (select vendor_id from products where id=?), 2, 'ACTIVE', now() - interval '1 minute')
                """, reservationId, customerId, productId, productId);
        jdbc.update("update product_inventory set reserved_quantity=2 where product_id=?", productId);

        mockMvc.perform(get("/api/v1/customer/reservations")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"EXPIRED\"")));

        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("select reserved_quantity from product_inventory where product_id=?",
                        Long.class, productId));
    }

    @Test
    void customerCannotReadOrReleaseAnotherCustomersReservation() throws Exception {
        UUID otherCustomer = createUser("other-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                insert into inventory_reservations
                    (id,customer_user_id,product_id,vendor_id,quantity,status,expires_at)
                values (?, ?, ?, (select vendor_id from products where id=?), 1, 'ACTIVE', now() + interval '15 minutes')
                """, reservationId, otherCustomer, productId, productId);
        jdbc.update("update product_inventory set reserved_quantity=1 where product_id=?", productId);

        mockMvc.perform(delete("/api/v1/customer/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE",
                jdbc.queryForObject("select status from inventory_reservations where id=?",
                        String.class, reservationId));
    }

    @Test
    void onlyCustomersCanUseReservationEndpoints() throws Exception {
        String email = "not-customer-" + UUID.randomUUID() + "@example.test";
        UUID vendorUser = createUser(email, "VENDOR");
        String token = jwtService.issueAccessToken(
                new MarketHubPrincipal(vendorUser, email, "not-used", UserRole.VENDOR, true));

        mockMvc.perform(get("/api/v1/customer/reservations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }
}
