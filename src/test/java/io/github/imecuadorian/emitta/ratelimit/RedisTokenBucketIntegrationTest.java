package io.github.imecuadorian.emitta.ratelimit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers
class RedisTokenBucketIntegrationTest {
 @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
 private LettuceConnectionFactory factory;
 private StringRedisTemplate redis;
 private SimpleMeterRegistry metrics;
 private RedisTokenBucket limiter;
 @BeforeEach void setup() {
  factory=factory();redis=new StringRedisTemplate(factory);metrics=new SimpleMeterRegistry();limiter=new RedisTokenBucket(redis,metrics);
 }
 @AfterEach void close() {SecurityContextHolder.clearContext();factory.destroy();metrics.close();}
 private LettuceConnectionFactory factory() {
  var f=new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(),REDIS.getMappedPort(6379)),
      LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).shutdownTimeout(Duration.ZERO).build());
  f.afterPropertiesSet();f.start();return f;
 }
 @Test void atomicLimitIsSharedByTwoInstancesUnderConcurrentTraffic() throws Exception {
  var second=factory();var otherMetrics=new SimpleMeterRegistry();
  try(var pool=Executors.newFixedThreadPool(20)) {
   var other=new RedisTokenBucket(new StringRedisTemplate(second),otherMetrics);
   var bucket=new RedisTokenBucket.Bucket("tenant-all",UUID.randomUUID().toString(),new RateLimitProperties.Policy(10,0.001));
   var start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
   for(int i=0;i<100;i++) {var target=i%2==0?limiter:other;futures.add(pool.submit(()->{start.await();return target.consume(List.of(bucket),"invoices").allowed();}));}
   start.countDown();int accepted=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))accepted++;
   assertEquals(10,accepted);
   assertFalse(limiter.consume(List.of(bucket),"invoices").allowed());
  } finally {second.destroy();otherMetrics.close();}
 }
 @Test void deniedMultiBucketRequestDoesNotConsumeOtherBucketsAndScriptCacheCanRecover() {
  String identity=UUID.randomUUID().toString();var blocked=new RedisTokenBucket.Bucket("client-all",identity,new RateLimitProperties.Policy(1,0.001));
  var fresh=new RedisTokenBucket.Bucket("tenant-all",identity,new RateLimitProperties.Policy(1,0.001));
  assertTrue(limiter.consume(List.of(blocked),"invoices").allowed());
  assertFalse(limiter.consume(List.of(blocked,fresh),"invoices").allowed());
  assertTrue(limiter.consume(List.of(fresh),"invoices").allowed());
  try(var connection=factory.getConnection()){connection.serverCommands().flushDb();connection.scriptingCommands().scriptFlush();}
  assertTrue(limiter.consume(List.of(fresh),"invoices").allowed());
 }
 @Test void authenticationUsesSocketIpAndIgnoresSpoofedForwardedHeaders() throws Exception {
  var policies=new RateLimitProperties(true,Map.of("ip",new RateLimitProperties.Policy(100,1),"ip-authentication",new RateLimitProperties.Policy(1,0.001)));
  var filter=new TrafficFilter(limiter,policies,false);
  String ip="192.0.2."+new Random().nextInt(200);
  for(int attempt=0;attempt<2;attempt++) {
   var request=new MockHttpServletRequest("POST","/api/v1/auth/token");request.setRemoteAddr(ip);request.addHeader("X-Forwarded-For","203.0.113."+attempt);
   var response=new MockHttpServletResponse();filter.doFilter(request,response,(a,b)->{});
   assertEquals(attempt==0?200:429,response.getStatus());
   if(attempt==1){assertNotNull(response.getHeader("Retry-After"));assertTrue(response.getContentAsString().contains("429"));}
  }
 }
 @Test void trustedTunnelSeparatesClientIpsAcrossInstances() throws Exception {
  var policies=new RateLimitProperties(true,Map.of("ip",new RateLimitProperties.Policy(100,1),"ip-authentication",new RateLimitProperties.Policy(1,0.001)));
  var peers=TrustedClientAddress.peers("192.0.2.10");
  var first=new TrafficFilter(limiter,policies,false,peers);
  var second=new TrafficFilter(limiter,policies,false,peers);
  String prefix="198.51.100.";int suffix=1+new Random().nextInt(100);
  for(int attempt=0;attempt<3;attempt++) {
   var request=new MockHttpServletRequest("POST","/api/v1/auth/token");request.setRemoteAddr("192.0.2.10");
   request.addHeader("CF-Connecting-IP",prefix+(suffix+(attempt==1?1:0)));
   var response=new MockHttpServletResponse();
   (attempt==0?first:second).doFilter(request,response,(a,b)->{});
   assertEquals(attempt<2?200:429,response.getStatus());
  }
 }
 @Test void clientAndTenantLimitsAreIsolatedAndSharedAcrossEndpoints() throws Exception {
  var props=new RateLimitProperties(true,Map.of("client",new RateLimitProperties.Policy(1,0.001),"tenant",new RateLimitProperties.Policy(10,0.001)));
  var filter=new TrafficFilter(limiter,props,true);String client=UUID.randomUUID().toString(),tenant=UUID.randomUUID().toString();
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("synthetic")
      .header("alg","RS256").claim("client_id",client).claim("tenant_id",tenant).build()));
  var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/v1/invoices"),response,(a,b)->{});assertEquals(200,response.getStatus());
  response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/api/v1/documents/1"),response,(a,b)->{});assertEquals(429,response.getStatus());
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("synthetic")
      .header("alg","RS256").claim("client_id",UUID.randomUUID().toString()).claim("tenant_id",UUID.randomUUID().toString()).build()));
  response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/v1/invoices"),response,(a,b)->{});assertEquals(200,response.getStatus());
 }
 @Test void redisOutageFailsClosedAndRecoversWithoutDisablingProtection() throws Exception {
  var filter=new TrafficFilter(limiter,new RateLimitProperties(true,Map.of()),false);
  var docker=REDIS.getDockerClient();docker.pauseContainerCmd(REDIS.getContainerId()).exec();
  try {
   var response=new MockHttpServletResponse();var request=new MockHttpServletRequest("POST","/api/v1/auth/token");request.setRemoteAddr("198.51.100.200");
   filter.doFilter(request,response,(a,b)->fail("Must not accept authentication during Redis outage"));
   assertEquals(503,response.getStatus());assertEquals(0.0,metrics.get("emitta.redis.available").gauge().value());
  } finally {docker.unpauseContainerCmd(REDIS.getContainerId()).exec();}
  factory.resetConnection();
  var response=new MockHttpServletResponse();var request=new MockHttpServletRequest("POST","/api/v1/auth/token");request.setRemoteAddr("198.51.100.201");
  filter.doFilter(request,response,(a,b)->{});assertEquals(200,response.getStatus());assertEquals(1.0,metrics.get("emitta.redis.available").gauge().value());
 }
}
