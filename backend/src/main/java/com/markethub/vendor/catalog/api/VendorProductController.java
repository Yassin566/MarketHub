package com.markethub.vendor.catalog.api;
import com.markethub.common.api.ApiResponse; import com.markethub.identity.security.MarketHubPrincipal; import jakarta.validation.Valid; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/v1/vendor/products") public class VendorProductController {
 private final VendorProductService service; public VendorProductController(VendorProductService service){this.service=service;}
 @GetMapping public ApiResponse<List<VendorProductService.ProductView>> list(@AuthenticationPrincipal MarketHubPrincipal p){return ApiResponse.ok(service.list(p.userId()));}
 @PostMapping public ApiResponse<VendorProductService.ProductView> create(@AuthenticationPrincipal MarketHubPrincipal p,@Valid @RequestBody VendorProductRequest r){return ApiResponse.ok(service.create(p.userId(),r));}
 @PutMapping("/{productId}") public ApiResponse<VendorProductService.ProductView> update(@AuthenticationPrincipal MarketHubPrincipal p,@PathVariable UUID productId,@Valid @RequestBody VendorProductRequest r){return ApiResponse.ok(service.update(p.userId(),productId,r));}
 @DeleteMapping("/{productId}") public ApiResponse<Void> archive(@AuthenticationPrincipal MarketHubPrincipal p,@PathVariable UUID productId){service.archive(p.userId(),productId);return ApiResponse.ok(null);}
}