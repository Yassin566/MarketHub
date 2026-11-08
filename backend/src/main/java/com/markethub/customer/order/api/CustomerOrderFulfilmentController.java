package com.markethub.customer.order.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderFulfilmentController {
    private final CustomerOrderFulfilmentService service;

    public CustomerOrderFulfilmentController(CustomerOrderFulfilmentService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}/fulfilments")
    public ApiResponse<List<CustomerOrderFulfilmentService.VendorFulfilment>> getFulfilments(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID orderId) {
        return ApiResponse.ok(service.get(principal.userId(), orderId));
    }
}
