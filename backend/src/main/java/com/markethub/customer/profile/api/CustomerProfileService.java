package com.markethub.customer.profile.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerProfileService {
    private final JdbcTemplate jdbc;

    public CustomerProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProfileView get(UUID customerId) {
        return jdbc.query("""
                SELECT first_name, last_name, phone
                FROM customer_profiles
                WHERE user_id = ?
                """, (rs, row) -> new ProfileView(
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("phone")), customerId)
                .stream()
                .findFirst()
                .orElse(new ProfileView("", "", ""));
    }

    @Transactional
    public ProfileView save(UUID customerId, CustomerProfileRequest request) {
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new IllegalArgumentException("First name and last name are required");
        }
        String phone = request.phone() == null || request.phone().isBlank()
                ? null : request.phone().trim();

        jdbc.update("""
                INSERT INTO customer_profiles (user_id, first_name, last_name, phone)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    first_name = EXCLUDED.first_name,
                    last_name = EXCLUDED.last_name,
                    phone = EXCLUDED.phone,
                    updated_at = now()
                """, customerId, firstName, lastName, phone);
        return get(customerId);
    }

    public record ProfileView(String firstName, String lastName, String phone) {}
}
