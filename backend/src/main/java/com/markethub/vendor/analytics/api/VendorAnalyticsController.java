package com.markethub.vendor.analytics.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/vendor/analytics")
@PreAuthorize("hasRole('VENDOR')")
public class VendorAnalyticsController {
    private final VendorAnalyticsService service;

    public VendorAnalyticsController(VendorAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<VendorAnalyticsService.Analytics> report(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.report(principal.userId(), from, to));
    }
}
