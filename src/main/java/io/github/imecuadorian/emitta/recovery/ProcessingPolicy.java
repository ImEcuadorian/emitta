package io.github.imecuadorian.emitta.recovery;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@ConfigurationProperties("emitta.processing.jobs")
public record ProcessingPolicy(int maxAttempts,Duration lease,Duration initialBackoff,Duration maximumBackoff,int batchSize) {
 public ProcessingPolicy {
  if(maxAttempts<1 || maxAttempts>20 || lease==null || lease.isNegative() || lease.isZero() || initialBackoff==null
    || initialBackoff.isNegative() || initialBackoff.isZero() || maximumBackoff==null || maximumBackoff.compareTo(initialBackoff)<0
    || batchSize<1 || batchSize>50) throw new IllegalArgumentException("Invalid durable processing policy");
 }
 public Duration delay(int attempt) {
  long ceiling=Math.min(maximumBackoff.toMillis(),initialBackoff.toMillis()*(1L<<Math.min(Math.max(0,attempt-1),20)));
  long jitter=java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1,ceiling/4+1));
  return Duration.ofMillis(Math.min(maximumBackoff.toMillis(),ceiling+jitter));
 }
}
