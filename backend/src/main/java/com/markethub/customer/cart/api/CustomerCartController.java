package com.markethub.customer.cart.api;
import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/customer/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerCartController {
    private final CustomerCartService carts;
    public CustomerCartController(CustomerCartService carts) { this.carts = carts; }
    @GetMapping
    public ApiResponse<CustomerCartService.CartView> get(@AuthenticationPrincipal MarketHubPrincipal principal) {
        return ApiResponse.ok(carts.get(principal.userId()));
    }
    @PutMapping("/items")
    public ApiResponse<CustomerCartService.CartView> putItem(@AuthenticationPrincipal MarketHubPrincipal principal,
            @Valid @RequestBody PutCartItemRequest request) {
        return ApiResponse.ok(carts.putItem(principal.userId(), request.productId(), request.quantity()));
    }
    @DeleteMapping("/items/{productId}")
    public ApiResponse<CustomerCartService.CartView> removeItem(@AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID productId) {
        return ApiResponse.ok(carts.removeItem(principal.userId(), productId));
    }
    @PostMapping("/checkout/validate")
    public ApiResponse<CustomerCartService.CheckoutValidation> validateCheckout(
            @AuthenticationPrincipal MarketHubPrincipal principal) {
        return ApiResponse.ok(carts.validateCheckout(principal.userId()));
    }
}
