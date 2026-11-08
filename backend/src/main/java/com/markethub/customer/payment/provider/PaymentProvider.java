package com.markethub.customer.payment.provider;

import java.math.BigDecimal;

public interface PaymentProvider {
    String providerCode();

    ProviderPayment createPayment(
            String idempotencyKey,
            BigDecimal amount,
            String currency);

    record ProviderPayment(String providerReference) {}
}
