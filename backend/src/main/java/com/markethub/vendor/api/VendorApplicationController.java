package com.markethub.vendor.api;
import com.markethub.common.api.ApiResponse;
import com.markethub.identity.security.MarketHubPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/vendor-applications")
public class VendorApplicationController {
 private final VendorApplicationService service;
 public VendorApplicationController(VendorApplicationService service){this.service=service;}
 @PostMapping public ApiResponse<VendorApplicationResult> apply(@AuthenticationPrincipal MarketHubPrincipal p,@Valid @RequestBody VendorApplicationRequest r){return ApiResponse.ok(service.apply(p.userId(),r));}
 public record VendorApplicationResult(UUID vendorId,String displayName,String status){}
}