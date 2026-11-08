package com.markethub.vendor.store.api;
import com.markethub.common.api.ApiResponse; import com.markethub.identity.security.MarketHubPrincipal; import jakarta.validation.Valid; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor/store") public class StoreProfileController {
 private final StoreProfileService service; public StoreProfileController(StoreProfileService service){this.service=service;}
 @GetMapping public ApiResponse<StoreProfileService.StoreView> get(@AuthenticationPrincipal MarketHubPrincipal p){return ApiResponse.ok(service.get(p.userId()));}
 @PostMapping public ApiResponse<StoreProfileService.StoreView> create(@AuthenticationPrincipal MarketHubPrincipal p,@Valid @RequestBody StoreProfileRequest r){return ApiResponse.ok(service.create(p.userId(),r));}
 @PutMapping public ApiResponse<StoreProfileService.StoreView> update(@AuthenticationPrincipal MarketHubPrincipal p,@Valid @RequestBody StoreProfileRequest r){return ApiResponse.ok(service.update(p.userId(),r));}
}