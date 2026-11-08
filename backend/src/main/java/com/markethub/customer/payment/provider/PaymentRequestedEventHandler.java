package com.markethub.customer.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markethub.common.outbox.OutboxEventHandler;
import com.markethub.common.outbox.TransactionalOutboxService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class PaymentRequestedEventHandler implements OutboxEventHandler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PaymentProvider provider;

    public PaymentRequestedEventHandler(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PaymentProvider provider) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.provider = provider;
    }

    private record PaymentDispatchState(String status, String providerCode, String providerReference) {}

    @Override
    public String eventType() {
        return "PAYMENT_REQUESTED";
    }

    /**
     * Deliberately not transactional: provider work must not hold database locks.
     * The payment UUID is the stable provider idempotency key across retries.
     */
    @Override
    public void handle(TransactionalOutboxService.OutboxMessage message) throws Exception {
        JsonNode payload = objectMapper.readTree(message.payload());
        UUID paymentId = UUID.fromString(requiredText(payload, "paymentId"));
        BigDecimal amount = new BigDecimal(requiredText(payload, "amount"));
        String currency = requiredText(payload, "currency");

        List<PaymentDispatchState> existing = jdbc.query("""
                SELECT status, provider_code, provider_reference
                FROM payments WHERE id = ?
                """, (rs, row) -> new PaymentDispatchState(
                rs.getString("status"), rs.getString("provider_code"),
                rs.getString("provider_reference")), paymentId);
        if (existing.isEmpty()) {
            throw new IllegalStateException("Payment record for outbox event does not exist");
        }

        PaymentDispatchState state = existing.getFirst();
        if (!"REQUESTED".equals(state.status())) {
            // A previously dispatched or terminal payment must not be recreated.
            return;
        }

        PaymentProvider.ProviderPayment providerPayment =
                provider.createPayment(paymentId.toString(), amount, currency);
        if (providerPayment.providerReference() == null
                || providerPayment.providerReference().isBlank()) {
            throw new IllegalStateException("Payment provider returned an empty reference");
        }

        int updated = jdbc.update("""
                UPDATE payments
                SET provider_code = ?, provider_reference = ?, status = 'PROCESSING',
                    updated_at = now()
                WHERE id = ? AND status = 'REQUESTED' AND provider_reference IS NULL
                """, provider.providerCode(), providerPayment.providerReference(), paymentId);
        if (updated == 1) {
            return;
        }

        // A concurrent retry may have persisted the same idempotent provider result.
        List<PaymentDispatchState> afterRace = jdbc.query("""
                SELECT status, provider_code, provider_reference
                FROM payments WHERE id = ?
                """, (rs, row) -> new PaymentDispatchState(
                rs.getString("status"), rs.getString("provider_code"),
                rs.getString("provider_reference")), paymentId);
        if (!afterRace.isEmpty()) {
            PaymentDispatchState persisted = afterRace.getFirst();
            if ("PROCESSING".equals(persisted.status())
                    && provider.providerCode().equals(persisted.providerCode())
                    && providerPayment.providerReference().equals(persisted.providerReference())) {
                return;
            }
            if (!"REQUESTED".equals(persisted.status())) {
                return;
            }
        }
        throw new IllegalStateException("Payment dispatch result could not be persisted safely");
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Outbox payment payload is missing " + field);
        }
        return value.asText();
    }
}
