package com.markethub.customer.review.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminProductReviewController {
    private final ProductReviewModerationService moderation;

    public AdminProductReviewController(ProductReviewModerationService moderation) {
        this.moderation = moderation;
    }

    public record PublicationRequest(@NotNull Boolean published) {}

    @GetMapping
    public ApiResponse<ProductReviewModerationService.ModerationPage> list(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(moderation.list(published, rating, productId, limit, offset));
    }

    @PatchMapping("/{reviewId}/publication")
    public ApiResponse<ProductReviewModerationService.ModerationView> setPublication(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @Valid @RequestBody PublicationRequest request) {
        return ApiResponse.ok(moderation.setPublished(reviewId, principal.userId(), request.published()));
    }
}
