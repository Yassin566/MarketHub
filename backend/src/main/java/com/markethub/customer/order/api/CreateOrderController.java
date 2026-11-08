package com.markethub.customer.order.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CreateOrderController {
    private final CustomerOrderService orders;

    public CreateOrderController(CustomerOrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/checkout")
    public ApiResponse<CustomerOrderService.OrderView> checkout(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        return ApiResponse.ok(orders.createFromCart(principal.userId(), idempotencyKey));
    }

    @GetMapping
    public ApiResponse<CustomerOrderService.OrderPage> history(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(orders.list(principal.userId(), limit, offset));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<CustomerOrderService.OrderView> get(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID orderId) {
        return ApiResponse.ok(orders.get(principal.userId(), orderId));
    }
}
