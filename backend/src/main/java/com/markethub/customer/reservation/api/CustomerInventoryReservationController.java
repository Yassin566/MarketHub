package com.markethub.customer.reservation.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/reservations")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerInventoryReservationController {
    private final CustomerInventoryReservationService reservations;

    public CustomerInventoryReservationController(CustomerInventoryReservationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping
    public ApiResponse<List<CustomerInventoryReservationService.ReservationView>> list(
            @AuthenticationPrincipal MarketHubPrincipal principal) {
        return ApiResponse.ok(reservations.list(principal.userId()));
    }

    @PostMapping
    public ApiResponse<CustomerInventoryReservationService.ReservationView> reserve(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @Valid @RequestBody CreateInventoryReservationRequest request) {
        return ApiResponse.ok(reservations.reserve(
                principal.userId(), request.productId(), request.quantity()));
    }

    @DeleteMapping("/{reservationId}")
    public ApiResponse<CustomerInventoryReservationService.ReservationView> release(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID reservationId) {
        return ApiResponse.ok(reservations.release(principal.userId(), reservationId));
    }
}
