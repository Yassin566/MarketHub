package com.markethub.vendor.admin.api;
import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/admin") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminVendorController {
 private final AdminVendorService service;
 public AdminVendorController(AdminVendorService service){this.service=service;}
 public record RejectVendorRequest(@NotBlank @Size(max=500) String reason){}
 public record StatusRequest(@NotBlank @Size(max=500) String reason){}
 @GetMapping("/vendor-applications") public ApiResponse<List<AdminVendorService.ApplicationView>> pending(){return ApiResponse.ok(service.pending());}
 @PostMapping("/vendor-applications/{vendorId}/approve") public ApiResponse<AdminVendorService.ApplicationView> approve(@PathVariable UUID vendorId,@AuthenticationPrincipal MarketHubPrincipal actor){return ApiResponse.ok(service.approve(vendorId,actor.userId()));}
 @PostMapping("/vendor-applications/{vendorId}/reject") public ApiResponse<AdminVendorService.ApplicationView> reject(@PathVariable UUID vendorId,@AuthenticationPrincipal MarketHubPrincipal actor,@Valid @RequestBody RejectVendorRequest request){return ApiResponse.ok(service.reject(vendorId,actor.userId(),request.reason()));}
 @GetMapping("/vendors") public ApiResponse<AdminVendorService.VendorPage> vendors(@RequestParam(required=false) String status,@RequestParam(required=false) String search,@RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset){return ApiResponse.ok(service.vendors(status,search,limit,offset));}
 @PatchMapping("/vendors/{vendorId}/suspension") public ApiResponse<AdminVendorService.VendorItem> suspend(@PathVariable UUID vendorId,@AuthenticationPrincipal MarketHubPrincipal actor,@Valid @RequestBody StatusRequest request){return ApiResponse.ok(service.changeStatus(vendorId,actor.userId(),"SUSPENDED",request.reason()));}
 @PatchMapping("/vendors/{vendorId}/reactivation") public ApiResponse<AdminVendorService.VendorItem> reactivate(@PathVariable UUID vendorId,@AuthenticationPrincipal MarketHubPrincipal actor,@Valid @RequestBody StatusRequest request){return ApiResponse.ok(service.changeStatus(vendorId,actor.userId(),"APPROVED",request.reason()));}
}
