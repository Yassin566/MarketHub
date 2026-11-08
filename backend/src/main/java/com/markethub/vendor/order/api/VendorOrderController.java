package com.markethub.vendor.order.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor/orders")
@PreAuthorize("hasRole('VENDOR')")
public class VendorOrderController {
    private final VendorOrderService service;

    public VendorOrderController(VendorOrderService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<VendorOrderService.OrderPage> list(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(service.list(principal.userId(), limit, offset));
    }

    @GetMapping("/{vendorOrderId}")
    public ApiResponse<VendorOrderService.OrderView> get(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID vendorOrderId) {
        return ApiResponse.ok(service.get(principal.userId(), vendorOrderId));
    }

    @PatchMapping("/{vendorOrderId}/status")
    public ApiResponse<VendorOrderService.OrderView> updateStatus(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID vendorOrderId,
            @Valid @RequestBody VendorOrderStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(principal.userId(), vendorOrderId,
                request.status(), request.note()));
    }
}
