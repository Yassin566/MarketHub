package com.markethub.customer.profile.api;

import com.markethub.identity.domain.UserRole;
import com.markethub.identity.security.JwtService;
import com.markethub.identity.security.MarketHubPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerProfileHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private UUID customerId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("profile-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        customerToken = token(customerId);
    }

    @Test
    void customerCanReadAnEmptyProfileAndSaveTheirOwnDetails() throws Exception {
        mockMvc.perform(get("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value(""))
                .andExpect(jsonPath("$.data.lastName").value(""));

        mockMvc.perform(put("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json")
                        .content("""
                                {"firstName":"  Ada ","lastName":" Lovelace  ","phone":" +216 20 123 456 "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Ada"))
                .andExpect(jsonPath("$.data.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.data.phone").value("+216 20 123 456"));

        mockMvc.perform(get("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Ada"));
    }

    @Test
    void customerCannotReadOrOverwriteAnotherCustomersProfile() throws Exception {
        UUID otherCustomer = createUser("profile-other-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        String otherToken = token(otherCustomer);

        mockMvc.perform(put("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Grace","lastName":"Hopper","phone":""}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value(""))
                .andExpect(jsonPath("$.data.lastName").value(""));
    }

    @Test
    void profileRequiresCustomerRoleAndValidNames() throws Exception {
        UUID vendorId = createUser("profile-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        mockMvc.perform(get("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + token(vendorId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/customer/profile"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/customer/profile")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json")
                        .content("""
                                {"firstName":" ","lastName":"","phone":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }

    private String token(UUID id) {
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, id);
        UserRole role = UserRole.valueOf(jdbc.queryForObject(
                "select role from app_users where id=?", String.class, id));
        return jwtService.issueAccessToken(new MarketHubPrincipal(id, email, "not-used", role, true));
    }
}
