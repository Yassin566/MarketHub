package com.markethub.customer.notification.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerNotificationHttpSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private CustomerNotificationService notificationService;

    private UUID customerId;
    private UUID notificationId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        customerId = createUser("notification-customer-" + UUID.randomUUID() + "@example.test", "CUSTOMER");
        customerToken = token(customerId);
        notificationId = insertNotification(customerId, "order:paid:" + UUID.randomUUID());
        insertNotification(customerId, "order:shipped:" + UUID.randomUUID());
    }

    @Test
    void customerCanListNotificationsAndUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/customer/notifications?limit=1&offset=0")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void customerCanFilterUnreadAndMarkOneRead() throws Exception {
        mockMvc.perform(patch("/api/v1/customer/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/customer/notifications?unreadOnly=true")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void customerCanMarkAllNotificationsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/customer/notifications/read-all")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        mockMvc.perform(get("/api/v1/customer/notifications")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void customerCannotReadOrMarkAnotherCustomersNotification() throws Exception {
        UUID otherCustomer = createUser("notification-other-" + UUID.randomUUID() + "@example.test", "CUSTOMER");

        mockMvc.perform(patch("/api/v1/customer/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + token(otherCustomer)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/customer/notifications")
                        .header("Authorization", "Bearer " + token(otherCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void notificationEndpointsRequireCustomerRoleAndAuthentication() throws Exception {
        UUID vendorId = createUser("notification-vendor-" + UUID.randomUUID() + "@example.test", "VENDOR");
        mockMvc.perform(get("/api/v1/customer/notifications")
                        .header("Authorization", "Bearer " + token(vendorId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/customer/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingTheSameBusinessEventTwiceIsIdempotent() {
        String eventKey = "test-event:" + UUID.randomUUID();
        var first = notificationService.create(customerId, null, eventKey,
                "ORDER", "Order update", "Your order is being prepared");
        var retry = notificationService.create(customerId, null, eventKey,
                "ORDER", "Order update", "Your order is being prepared");

        org.assertj.core.api.Assertions.assertThat(retry.id()).isEqualTo(first.id());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from customer_notifications where customer_user_id=? and event_key=?",
                Long.class, customerId, eventKey)).isEqualTo(1L);
    }

    @Test
    void notificationHistoryRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/customer/notifications?limit=101")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/customer/notifications?offset=-1")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest());
    }

    private UUID insertNotification(UUID owner, String eventKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_notifications
                    (id, customer_user_id, event_key, type, title, message)
                VALUES (?, ?, ?, 'ORDER', 'Order update', 'Your order status changed')
                """, id, owner, eventKey);
        return id;
    }

    private UUID createUser(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",
                id, email, "integration-test-hash", role);
        return id;
    }

    private String token(UUID id) {
        String email = jdbc.queryForObject("select email from app_users where id=?", String.class, id);
        return jwtService.issueAccessToken(
                new MarketHubPrincipal(id, email, "not-used", UserRole.valueOf(
                        jdbc.queryForObject("select role from app_users where id=?", String.class, id)), true));
    }
}
