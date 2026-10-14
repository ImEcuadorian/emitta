package io.github.imecuadorian.emitta.ratelimit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;
@ConfigurationProperties("emitta.security.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String,Policy> policies) {
 public record Policy(long capacity, double refillPerSecond) {
  public Policy { if(capacity<1 || capacity>1000000 || !Double.isFinite(refillPerSecond) || refillPerSecond<=0) throw new IllegalArgumentException("Invalid rate policy"); }
 }
 public RateLimitProperties { policies=policies==null?Map.of():Map.copyOf(policies); }
 public Policy policy(String dimension,String endpoint) {
  var specific=policies.get(dimension+"-"+endpoint);
  return specific!=null?specific:policies.getOrDefault(dimension,new Policy(120,2));
 }
}
