package io.github.imecuadorian.emitta.shared.resilience;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.sriauthorization.application.exception.SriAuthorizationException;
import static org.junit.jupiter.api.Assertions.*;
class SriCallGuardTest {
 private SriResilienceProperties.Operation policy(int retries,int minimum) {return new SriResilienceProperties.Operation(1,10,minimum,50,Duration.ofMillis(10),retries,Duration.ofMillis(1));}
 @Test void receptionNeverRetriesAndCircuitRecoversViaHalfOpen() {
  var metrics=new SimpleMeterRegistry();var guard=new SriCallGuard("reception",policy(1,1),metrics);var calls=new AtomicInteger();
  assertThrows(SriReceptionException.class,()->guard.execute(()->{calls.incrementAndGet();throw new SriReceptionException("synthetic",new IOException());}));
  assertEquals(1,calls.get());assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());
  assertThrows(CallNotPermittedException.class,()->guard.execute(()->{calls.incrementAndGet();return "forbidden";}));assertEquals(1,calls.get());
  guard.circuit().transitionToHalfOpenState();assertEquals("ok",guard.execute(()->"ok"));assertEquals("ok",guard.execute(()->"ok"));
  assertEquals(CircuitBreaker.State.CLOSED,guard.circuit().getState());metrics.close();
 }
 @Test void authorizationHasBoundedRetriesButDoesNotRetryValidationOrDefinitiveResults() {
  var metrics=new SimpleMeterRegistry();var guard=new SriCallGuard("authorization",policy(2,10),metrics);var calls=new AtomicInteger();
  assertThrows(SriAuthorizationException.class,()->guard.execute(()->{calls.incrementAndGet();throw new SriAuthorizationException("synthetic",new IOException());}));assertEquals(2,calls.get());
  calls.set(0);assertThrows(SriAuthorizationException.class,()->guard.execute(()->{calls.incrementAndGet();throw new SriAuthorizationException("invalid response");}));assertEquals(1,calls.get());
  calls.set(0);assertEquals("NOT_AUTHORIZED",guard.execute(()->{calls.incrementAndGet();return "NOT_AUTHORIZED";}));assertEquals(1,calls.get());metrics.close();
 }
 @Test void bulkheadRejectsExcessConcurrencyWithoutRetrying() throws Exception {
  var metrics=new SimpleMeterRegistry();var guard=new SriCallGuard("authorization",policy(2,10),metrics);
  var started=new CountDownLatch(1);var finish=new CountDownLatch(1);
  try(var pool=Executors.newSingleThreadExecutor()) {
   var first=pool.submit(()->guard.execute(()->{started.countDown();try{finish.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return "ok";}));
   assertTrue(started.await(2,TimeUnit.SECONDS));
   try {assertThrows(BulkheadFullException.class,()->guard.execute(()->"unexpected"));}finally{finish.countDown();}
   assertEquals("ok",first.get(2,TimeUnit.SECONDS));
  }finally{metrics.close();}
 }
 @Test void realBlockingSoapTimeoutIsAppliedAndReceptionOutcomeStaysAmbiguous() throws Exception {
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var calls=new AtomicInteger();server.createContext("/soap",exchange->{calls.incrementAndGet();try{Thread.sleep(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});
  var pool=Executors.newCachedThreadPool();server.setExecutor(pool);server.start();var metrics=new SimpleMeterRegistry();
  try {
   var uri=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/soap");
   var client=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(750)).build();
   var reception=new io.github.imecuadorian.emitta.srireception.adapter.out.soap.SriSoapReceptionAdapter(client,uri,uri,Duration.ofMillis(750));
   var guard=new SriCallGuard("reception",policy(1,10),metrics);
   assertThrows(SriReceptionException.class,()->guard.execute(()->reception.submit(new io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest(
     io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment.TEST,"<factura/>".getBytes()))));
   assertEquals(1,calls.get());
  }finally{server.stop(0);pool.shutdownNow();metrics.close();}
 }
 @Test void bothSoapAdaptersBoundBodyReadAfterResponseHeadersHaveArrived() throws Exception {
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var calls=new AtomicInteger();server.createContext("/soap",exchange->{
   calls.incrementAndGet();exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write("<partial".getBytes());exchange.getResponseBody().flush();
   try{Thread.sleep(4000);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}
  });
  var pool=Executors.newCachedThreadPool();server.setExecutor(pool);server.start();
  try {
   var uri=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/soap");
   var client=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
   var reception=new io.github.imecuadorian.emitta.srireception.adapter.out.soap.SriSoapReceptionAdapter(client,uri,uri,Duration.ofSeconds(1));
   var authorization=new io.github.imecuadorian.emitta.sriauthorization.adapter.out.soap.SriSoapAuthorizationAdapter(client,uri,uri,Duration.ofSeconds(1));
   long started=System.nanoTime();
   var receipt=assertThrows(SriReceptionException.class,()->reception.submit(new io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest(io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment.TEST,"<factura/>".getBytes())));
   assertTrue(SriCallGuard.communicationFailure(receipt));assertTrue(Duration.ofNanos(System.nanoTime()-started).compareTo(Duration.ofSeconds(3))<0);
   started=System.nanoTime();
   var query=assertThrows(SriAuthorizationException.class,()->authorization.query(new io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest(io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment.TEST,"1".repeat(49))));
   assertTrue(SriCallGuard.communicationFailure(query));assertTrue(Duration.ofNanos(System.nanoTime()-started).compareTo(Duration.ofSeconds(3))<0);assertEquals(2,calls.get());
  }finally{server.stop(0);pool.shutdownNow();}
 }

}
