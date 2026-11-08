package com.markethub.vendor.admin.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminPaymentResolutionController {
    private final AdminPaymentResolutionService resolutions;

    public AdminPaymentResolutionController(AdminPaymentResolutionService resolutions) {
        this.resolutions = resolutions;
    }

    @PostMapping("/{paymentId}/resolution")
    public ApiResponse<AdminPaymentResolutionService.ResolutionView> resolve(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @RequestBody AdminPaymentResolutionService.ResolutionRequest request) {
        return ApiResponse.ok(resolutions.resolve(paymentId, principal.userId(), request));
    }

    @GetMapping("/{paymentId}/resolution")
    public ApiResponse<AdminPaymentResolutionService.ResolutionView> getResolution(
            @PathVariable UUID paymentId) {
        return ApiResponse.ok(resolutions.getResolution(paymentId));
    }
}
