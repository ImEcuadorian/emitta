package io.github.imecuadorian.emitta.shared.resilience;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@ConfigurationProperties("emitta.sri.resilience")
public record SriResilienceProperties(Operation reception,Operation authorization) {
 public record Operation(int concurrentCalls,int windowSize,int minimumCalls,float failureThreshold,
        Duration openWait,int maxAttempts,Duration retryDelay) {
  public Operation {
   if(concurrentCalls<1 || windowSize<2 || minimumCalls<1 || minimumCalls>windowSize || failureThreshold<=0 || failureThreshold>100
      || openWait==null || openWait.isNegative() || openWait.isZero() || maxAttempts<1 || maxAttempts>5
      || retryDelay==null || retryDelay.isNegative()) throw new IllegalArgumentException("Invalid SRI resilience policy");
  }
 }
 public SriResilienceProperties {
  if(reception==null) reception=defaults(1);
  if(authorization==null) authorization=defaults(2);
  if(reception.maxAttempts()!=1) throw new IllegalArgumentException("Reception must never retry");
 }
 private static Operation defaults(int attempts) { return new Operation(8,20,10,50,Duration.ofSeconds(15),attempts,Duration.ofMillis(200)); }
}
