package com.markethub.customer.review.api;

import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ProductReviewController {
    private final ProductReviewService reviews;

    public ProductReviewController(ProductReviewService reviews) {
        this.reviews = reviews;
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String reviewText) {}

    @GetMapping("/api/v1/products/{productId}/reviews")
    public ApiResponse<ProductReviewService.ReviewPage> list(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(reviews.listPublished(productId, limit, offset));
    }

    @PostMapping("/api/v1/customer/products/{productId}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ProductReviewService.Review> create(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID productId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviews.create(principal.userId(), productId,
                request.rating(), request.reviewText()));
    }

    @PutMapping("/api/v1/customer/reviews/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ProductReviewService.Review> update(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviews.update(principal.userId(), reviewId,
                request.rating(), request.reviewText()));
    }

    @DeleteMapping("/api/v1/customer/reviews/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal MarketHubPrincipal principal,
            @PathVariable UUID reviewId) {
        reviews.delete(principal.userId(), reviewId);
        return ApiResponse.ok(null);
    }
}
