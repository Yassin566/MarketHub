package com.markethub.customer.profile.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/profile")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {
    private final CustomerProfileService profiles;

    public CustomerProfileController(CustomerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ApiResponse<CustomerProfileService.ProfileView> get(
            @AuthenticationPrincipal MarketHubPrincipal principal) {
        return ApiResponse.ok(profiles.get(principal.userId()));
    }

    @PutMapping
    public ApiResponse<CustomerProfileService.ProfileView> save(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @Valid @RequestBody CustomerProfileRequest request) {
        return ApiResponse.ok(profiles.save(principal.userId(), request));
    }
}
