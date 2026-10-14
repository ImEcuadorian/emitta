package io.github.imecuadorian.emitta.ratelimit;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Redis TIME and one atomic multi-bucket decision; keys share a Redis Cluster slot. */
public final class RedisTokenBucket {
 public record Bucket(String dimension,String identity,RateLimitProperties.Policy policy) {}
 public record Decision(boolean allowed,long retryAfterSeconds) {}
 @SuppressWarnings("rawtypes") private final DefaultRedisScript<List> script=new DefaultRedisScript<>();
 private final StringRedisTemplate redis; private final MeterRegistry metrics;
 private final AtomicInteger available=new AtomicInteger(-1);
 public RedisTokenBucket(StringRedisTemplate redis,MeterRegistry metrics) {
  this.redis=redis;this.metrics=metrics;script.setLocation(new ClassPathResource("redis/token-bucket.lua"));script.setResultType(List.class);
  metrics.gauge("emitta.redis.available",available);
 }
 public Decision consume(List<Bucket> buckets,String endpoint) {
  if(buckets.isEmpty()) return new Decision(true,0);
  var keys=new ArrayList<String>();var args=new ArrayList<String>();
  for(var b:buckets) {
   keys.add("emitta:{traffic}:"+b.dimension()+":"+digest(b.identity()));
   args.add(Long.toString(b.policy().capacity()));args.add(Double.toString(b.policy().refillPerSecond()));
  }
  try {
   var result=redis.execute(script,keys,args.toArray());
   if(result==null || result.size()!=2) throw new IllegalStateException("Rate backend returned invalid response");
   available.set(1);boolean allowed=((Number)result.get(0)).longValue()==1;
   metrics.counter("emitta.http.traffic","endpoint",endpoint,"result",allowed?"accepted":"limited").increment();
   return new Decision(allowed,((Number)result.get(1)).longValue());
  } catch(RuntimeException failure) {
   available.set(0);metrics.counter("emitta.http.traffic","endpoint",endpoint,"result","unavailable").increment();
   throw new RateLimitUnavailableException(); // no secrets or Redis endpoint in HTTP response
  }
 }
 private static String digest(String identity) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))); }
  catch(Exception e) { throw new IllegalStateException("SHA-256 unavailable",e); }
 }
 public static final class RateLimitUnavailableException extends RuntimeException {}
}
