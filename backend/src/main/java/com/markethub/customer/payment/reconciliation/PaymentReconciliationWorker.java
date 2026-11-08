package com.markethub.customer.payment.reconciliation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
@ConditionalOnProperty(prefix="markethub.payment.reconciliation.worker", name="enabled", havingValue="true")
public class PaymentReconciliationWorker {
 private final PaymentReconciliationService service;
 private final Duration staleAfter;
 private final int batchSize;
 public PaymentReconciliationWorker(PaymentReconciliationService service,
   @org.springframework.beans.factory.annotation.Value("${markethub.payment.reconciliation.worker.stale-after:PT30M}") Duration staleAfter,
   @org.springframework.beans.factory.annotation.Value("${markethub.payment.reconciliation.worker.batch-size:50}") int batchSize) {
  this.service=service; this.staleAfter=staleAfter; this.batchSize=batchSize;
 }
 @Scheduled(fixedDelayString="${markethub.payment.reconciliation.worker.poll-interval:PT1M}")
 public void reconcile(){ service.flagStalePayments(staleAfter,batchSize); }
}
