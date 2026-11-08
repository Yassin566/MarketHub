package com.markethub.vendor.inventory.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor/inventory")
public class VendorInventoryController {

    private final VendorInventoryService service;

    public VendorInventoryController(VendorInventoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<VendorInventoryService.InventoryView>> list(
            @AuthenticationPrincipal MarketHubPrincipal principal) {
        return ApiResponse.ok(service.list(principal.userId()));
    }

    @PostMapping("/{productId}/adjustments")
    public ApiResponse<VendorInventoryService.AdjustmentView> adjust(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        return ApiResponse.ok(service.adjust(principal.userId(), productId, request));
    }

    @GetMapping("/{productId}/adjustments")
    public ApiResponse<List<VendorInventoryService.AdjustmentView>> history(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID productId) {
        return ApiResponse.ok(service.history(principal.userId(), productId));
    }
}
