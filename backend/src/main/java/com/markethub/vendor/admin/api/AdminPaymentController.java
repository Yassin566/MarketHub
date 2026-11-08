package com.markethub.vendor.admin.api;

import com.markethub.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminPaymentController {
    private final AdminPaymentService payments;

    public AdminPaymentController(AdminPaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public ApiResponse<AdminPaymentService.PaymentPage> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(payments.list(status, limit, offset));
    }

    @GetMapping("/{paymentId}/events")
    public ApiResponse<AdminPaymentService.ProviderEventHistory> events(
            @PathVariable UUID paymentId) {
        return ApiResponse.ok(payments.events(paymentId));
    }
}
