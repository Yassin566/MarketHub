package com.markethub.customer.notification.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerNotificationController {
    private final CustomerNotificationService notifications;

    public CustomerNotificationController(CustomerNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<CustomerNotificationService.NotificationPage> list(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.ok(notifications.list(principal.userId(), limit, offset, unreadOnly));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<CustomerNotificationService.Notification> markRead(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID notificationId) {
        return ApiResponse.ok(notifications.markRead(principal.userId(), notificationId));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Long> markAllRead(@AuthenticationPrincipal MarketHubPrincipal principal) {
        return ApiResponse.ok(notifications.markAllRead(principal.userId()));
    }
}
