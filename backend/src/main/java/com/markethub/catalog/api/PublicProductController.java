package com.markethub.catalog.api;

import com.markethub.catalog.domain.Product;
import com.markethub.catalog.repository.ProductRepository;
import com.markethub.common.api.ApiResponse;
import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class PublicProductController {
    private final ProductRepository products;

    public PublicProductController(ProductRepository products) {
        this.products = products;
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
        return ApiResponse.ok(result.map(PublicProductController::view));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductView> get(@PathVariable String slug) {
        Product product = products.findBySlugAndStatus(slug, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return ApiResponse.ok(view(product));
    }

    private static ProductView view(Product product) {
        return new ProductView(product.getId(), product.getVendorId(), product.getName(),
                product.getSlug(), product.getPrice(), product.getCurrency(), product.getCreatedAt());
    }

    public record ProductView(UUID id, UUID vendorId, String name, String slug,
                              java.math.BigDecimal price, String currency,
                              java.time.Instant createdAt) {}
}
