package com.markethub.catalog.api;

import com.markethub.catalog.domain.Product;
import com.markethub.catalog.repository.ProductRepository;
import com.markethub.customer.review.api.ProductReviewService;
import com.markethub.common.api.ApiResponse;
import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class PublicProductController {
    private final ProductRepository products;
    private final ProductReviewService reviews;

    public PublicProductController(ProductRepository products, ProductReviewService reviews) {
        this.products = products;
        this.reviews = reviews;
    }

    @GetMapping
    public ApiResponse<Page<ProductView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String search) {
        if (page < 0 || size < 1 || size > 48) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> result = search == null || search.isBlank()
                ? products.findByStatus("ACTIVE", pageable)
                : products.findByStatusAndNameContainingIgnoreCase("ACTIVE", search.trim(), pageable);
        List<UUID> productIds = result.getContent().stream().map(Product::getId).toList();
        Map<UUID, ProductReviewService.ReviewSummary> summaries = reviews.summariesFor(productIds);
        return ApiResponse.ok(result.map(product -> view(product, summaries.get(product.getId()))));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductView> get(@PathVariable String slug) {
        Product product = products.findBySlugAndStatus(slug, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return ApiResponse.ok(view(product, reviews.summariesFor(List.of(product.getId())).get(product.getId())));
    }

    private static ProductView view(Product product, ProductReviewService.ReviewSummary summary) {
        return new ProductView(product.getId(), product.getVendorId(), product.getName(),
                product.getSlug(), product.getPrice(), product.getCurrency(), product.getCreatedAt(),
                summary == null ? null : summary.averageRating(),
                summary == null ? 0 : summary.reviewCount());
    }

    public record ProductView(UUID id, UUID vendorId, String name, String slug,
                              BigDecimal price, String currency, Instant createdAt,
                              BigDecimal averageRating, long reviewCount) {}
}
