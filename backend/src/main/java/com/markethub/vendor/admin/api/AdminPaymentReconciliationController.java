package com.markethub.vendor.admin.api;
import com.markethub.common.api.ApiResponse;
import com.markethub.customer.payment.reconciliation.PaymentReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/payments/reconciliation")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminPaymentReconciliationController {
 private final PaymentReconciliationService reconciliation;
 public AdminPaymentReconciliationController(PaymentReconciliationService reconciliation){this.reconciliation=reconciliation;}
 @GetMapping
 public ApiResponse<PaymentReconciliationService.ReconciliationPage> list(
  @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){
  return ApiResponse.ok(reconciliation.list(limit,offset));
 }
}
