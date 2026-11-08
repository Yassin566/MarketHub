package com.markethub.customer.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deterministic local adapter. It never contacts a payment network or moves money.
 * Repeated calls with the same idempotency key return the same provider reference.
 */
@Component
@ConditionalOnProperty(
        prefix = "markethub.payment.provider",
        name = "mode",
        havingValue = "sandbox",
        matchIfMissing = true)
public class SandboxPaymentProvider implements PaymentProvider {
    @Override
    public String providerCode() {
        return "SANDBOX";
    }

    @Override
    public ProviderPayment createPayment(String idempotencyKey, BigDecimal amount, String currency) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("provider idempotency key is required");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("payment amount must be zero or greater");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }

        UUID reference = UUID.nameUUIDFromBytes(
                ("markethub-sandbox:" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        return new ProviderPayment("sandbox-" + reference);
    }
}
