package com.markethub.customer.payment.reconciliation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
@Service
public class PaymentReconciliationService {
 private static final String OUTCOME="STALE_FLAGGED_FOR_REVIEW";
 private static final String DETAIL="Processing timeout; provider status was not queried. Manual verification is required.";
 private final JdbcTemplate jdbc;
 public PaymentReconciliationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record ReconciliationView(UUID attemptId,UUID paymentId,UUID orderId,String providerCode,String providerReference,String outcome,String detail,String observedPaymentStatus,java.time.Instant createdAt){}
 public record ReconciliationPage(List<ReconciliationView> items,long total,int limit,int offset){}
 @Transactional
 public int flagStalePayments(Duration staleAfter,int batchSize){
  if(staleAfter==null||staleAfter.toMillis()<1000) throw new IllegalArgumentException("staleAfter must be at least one second");
  if(batchSize<1||batchSize>500) throw new IllegalArgumentException("batchSize must be between 1 and 500");
  List<UUID> ids=jdbc.query("""
   SELECT id FROM payments WHERE status='PROCESSING'
   AND updated_at <= now() - (? * interval '1 second')
   ORDER BY updated_at,id LIMIT ? FOR UPDATE SKIP LOCKED
   """,(rs,row)->rs.getObject("id",UUID.class),staleAfter.toSeconds(),batchSize);
  for(UUID id:ids){
   jdbc.update("""
    INSERT INTO payment_reconciliation_attempts(id,payment_id,attempt_number,outcome,detail,observed_payment_status)
    SELECT ?,?,COALESCE(MAX(attempt_number),0)+1,?,?,'PROCESSING'
    FROM payment_reconciliation_attempts WHERE payment_id=?
    """,UUID.randomUUID(),id,OUTCOME,DETAIL,id);
   jdbc.update("UPDATE payments SET status='REQUIRES_REVIEW',updated_at=now() WHERE id=? AND status='PROCESSING'",id);
  }
  return ids.size();
 }
 @Transactional(readOnly=true)
 public ReconciliationPage list(int limit,int offset){
  if(limit<1||limit>100) throw new IllegalArgumentException("Reconciliation page limit must be between 1 and 100");
  if(offset<0) throw new IllegalArgumentException("Reconciliation page offset cannot be negative");
  Long total=jdbc.queryForObject("SELECT count(*) FROM payment_reconciliation_attempts",Long.class);
  List<ReconciliationView> items=jdbc.query("""
   SELECT a.id,a.payment_id,p.order_id,p.provider_code,p.provider_reference,a.outcome,a.detail,a.observed_payment_status,a.created_at
   FROM payment_reconciliation_attempts a JOIN payments p ON p.id=a.payment_id
   ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?
   """,(rs,row)->new ReconciliationView(rs.getObject("id",UUID.class),rs.getObject("payment_id",UUID.class),
   rs.getObject("order_id",UUID.class),rs.getString("provider_code"),rs.getString("provider_reference"),
   rs.getString("outcome"),rs.getString("detail"),rs.getString("observed_payment_status"),
   rs.getTimestamp("created_at").toInstant()),limit,offset);
  return new ReconciliationPage(items,total==null?0:total,limit,offset);
 }
}
