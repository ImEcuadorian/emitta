package io.github.imecuadorian.emitta.ratelimit;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.io.IOException;
import java.util.*;
/** Forwarded identity is accepted only from explicitly configured socket peers. */
public final class TrafficFilter extends OncePerRequestFilter {
 private final RedisTokenBucket limiter;private final RateLimitProperties properties;private final boolean identityPhase;
 private final Set<String> trustedPeers;
 public TrafficFilter(RedisTokenBucket limiter,RateLimitProperties properties,boolean identityPhase) {
  this(limiter,properties,identityPhase,Set.of());
 }
 public TrafficFilter(RedisTokenBucket limiter,RateLimitProperties properties,boolean identityPhase,Set<String> trustedPeers) {
  this.limiter=limiter;this.properties=properties;this.identityPhase=identityPhase;this.trustedPeers=Set.copyOf(trustedPeers);
 }
 @Override protected String getAlreadyFilteredAttributeName() { return getClass().getName()+"."+identityPhase; }
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  String path=request.getRequestURI().substring(request.getContextPath().length());
  if(!path.startsWith("/api/")) { chain.doFilter(request,response);return; }
  String endpoint=endpoint(path);var buckets=new ArrayList<RedisTokenBucket.Bucket>();
  if(!identityPhase) {
   // Spring must retain the original socket peer: forward-headers-strategy NONE.
   String address=TrustedClientAddress.resolve(request,trustedPeers);
   buckets.add(bucket("ip",address,"all"));
   buckets.add(bucket("ip",address,endpoint));
  } else if(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
   String client=jwt.getToken().getClaimAsString("client_id"),tenant=jwt.getToken().getClaimAsString("tenant_id");
   if(client!=null) { buckets.add(bucket("client",client,"all"));buckets.add(bucket("client",client,endpoint)); }
   if(tenant!=null) { buckets.add(bucket("tenant",tenant,"all"));buckets.add(bucket("tenant",tenant,endpoint)); }
  }
  try {
   var decision=limiter.consume(buckets,endpoint);
   if(!decision.allowed()) { error(response,429,"Traffic limit exceeded",decision.retryAfterSeconds());return; }
  } catch(RedisTokenBucket.RateLimitUnavailableException unavailable) {
   // Fail closed for incoming HTTP. Already-persisted fiscal work is independent of Redis.
   error(response,503,"Traffic protection temporarily unavailable",1);return;
  }
  chain.doFilter(request,response);
 }
 private RedisTokenBucket.Bucket bucket(String dimension,String identity,String endpoint) {
  return new RedisTokenBucket.Bucket(dimension+"-"+endpoint,identity,properties.policy(dimension,endpoint));
 }
 public static String endpoint(String path) {
  if(path.equals("/api/v1/auth/token"))return "authentication";
  if(path.equals("/api/v1/invoices"))return "invoices";
  if(path.equals("/api/v1/fiscal-configuration") || path.startsWith("/api/v1/tenants/") && path.endsWith("/fiscal-configuration"))return "fiscal-configuration";
  if(path.startsWith("/api/v1/taxpayers/") && path.endsWith("/certificates"))return "certificates";
  if(path.startsWith("/api/v1/tenants/") && path.contains("/documents/"))return "artifacts";
  if(path.startsWith("/api/v1/tenants/") && path.endsWith("/documents"))return "documents";
  if(path.startsWith("/api/v1/documents/")) {
   if(path.contains("/artifacts/"))return "artifacts";
   return path.endsWith("/ride-job")?"ride-status":"documents";
  }
  if(path.equals("/api/v1/tenants"))return "tenants";
  if(path.startsWith("/api/v1/tenants/") && path.endsWith("/taxpayers"))return "taxpayers";
  if(path.startsWith("/api/v1/taxpayers/") && path.endsWith("/establishments"))return "establishments";
  if(path.startsWith("/api/v1/establishments/") && path.endsWith("/points-of-issue"))return "points-of-issue";
  return "other";
 }
 public static void error(HttpServletResponse response,int status,String title,long retry) throws IOException {
  response.setStatus(status);response.setContentType("application/problem+json");response.setHeader("Cache-Control","no-store");
  response.setHeader("Retry-After",Long.toString(Math.max(1,retry)));
  response.getWriter().write("{\"type\":\"about:blank\",\"status\":"+status+",\"title\":\""+title+"\"}");
 }
}
