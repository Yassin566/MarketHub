package com.markethub.customer.payment.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders/{orderId}/payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerPaymentController {
    private final CustomerPaymentService payments;

    public CustomerPaymentController(CustomerPaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ApiResponse<CustomerPaymentService.PaymentView> request(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        return ApiResponse.ok(payments.requestPayment(principal.userId(), orderId, idempotencyKey));
    }
}
