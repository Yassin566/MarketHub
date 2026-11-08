package com.markethub.customer.payment.reconciliation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
class PaymentReconciliationServiceTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final PaymentReconciliationService service=new PaymentReconciliationService(jdbc);
 @Test void rejectsInvalidThresholdBeforeDatabaseAccess(){
  assertThrows(IllegalArgumentException.class,()->service.flagStalePayments(Duration.ZERO,50));
  assertThrows(IllegalArgumentException.class,()->service.flagStalePayments(Duration.ofMillis(500),50));
  assertThrows(IllegalArgumentException.class,()->service.flagStalePayments(Duration.ofMinutes(-1),50));
  verifyNoInteractions(jdbc);
 }
 @Test void rejectsInvalidBatchBeforeDatabaseAccess(){
  assertThrows(IllegalArgumentException.class,()->service.flagStalePayments(Duration.ofMinutes(30),0));
  assertThrows(IllegalArgumentException.class,()->service.flagStalePayments(Duration.ofMinutes(30),501));
  verifyNoInteractions(jdbc);
 }
 @Test void rejectsInvalidPaginationBeforeDatabaseAccess(){
  assertThrows(IllegalArgumentException.class,()->service.list(101,0));
  assertThrows(IllegalArgumentException.class,()->service.list(0,0));
  assertThrows(IllegalArgumentException.class,()->service.list(50,-1));
  verifyNoInteractions(jdbc);
 }
}
