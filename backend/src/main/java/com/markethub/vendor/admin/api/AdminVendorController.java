package com.markethub.vendor.admin.api;
import com.markethub.common.api.ApiResponse; import com.markethub.identity.security.MarketHubPrincipal; import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/vendor-applications") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminVendorController {
 private final AdminVendorService service; public AdminVendorController(AdminVendorService service){this.service=service;}
 @GetMapping public ApiResponse<List<AdminVendorService.ApplicationView>> pending(){return ApiResponse.ok(service.pending());}
 @PostMapping("/{vendorId}/approve") public ApiResponse<AdminVendorService.ApplicationView> approve(@PathVariable UUID vendorId,@AuthenticationPrincipal MarketHubPrincipal actor){return ApiResponse.ok(service.approve(vendorId,actor.userId()));}
 @PostMapping("/{vendorId}/reject") public ApiResponse<AdminVendorService.ApplicationView> reject(@PathVariable UUID vendorId,@AuthenticationPrincipal MarketHubPrincipal actor,@Valid @RequestBody RejectVendorRequest request){return ApiResponse.ok(service.reject(vendorId,actor.userId(),request.reason()));}
 public record RejectVendorRequest(@NotBlank @Size(max=500) String reason){}
}