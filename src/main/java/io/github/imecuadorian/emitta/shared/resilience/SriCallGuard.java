package io.github.imecuadorian.emitta.shared.resilience;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.retry.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;
/** Reception has exactly one invocation; authorization retries only communication failures. */
public final class SriCallGuard {
 private final CircuitBreaker circuit;private final Bulkhead bulkhead;private final Retry retry;
 private final String operation;private final MeterRegistry metrics;
 public SriCallGuard(String operation,SriResilienceProperties.Operation policy,MeterRegistry metrics) {
  this.operation=operation;this.metrics=metrics;
  if(operation.equals("reception") && policy.maxAttempts()!=1) throw new IllegalArgumentException("Reception retries forbidden");
  circuit=CircuitBreaker.of("sri-"+operation,CircuitBreakerConfig.custom().slidingWindowSize(policy.windowSize())
    .minimumNumberOfCalls(policy.minimumCalls()).failureRateThreshold(policy.failureThreshold())
    .waitDurationInOpenState(policy.openWait()).permittedNumberOfCallsInHalfOpenState(2)
    .recordException(SriCallGuard::communicationFailure).build());
  bulkhead=Bulkhead.of("sri-"+operation,BulkheadConfig.custom().maxConcurrentCalls(policy.concurrentCalls())
    .maxWaitDuration(java.time.Duration.ZERO).build());
  retry=Retry.of("sri-"+operation,RetryConfig.custom().maxAttempts(policy.maxAttempts())
    .retryOnException(SriCallGuard::communicationFailure)
    .intervalFunction(attempt -> Math.min(2000L,policy.retryDelay().toMillis()*(1L<<Math.min(attempt-1,4)))
        +ThreadLocalRandom.current().nextLong(Math.max(1,policy.retryDelay().toMillis()/4+1))).build());
  retry.getEventPublisher().onRetry(e -> metrics.counter("emitta.sri.retries","operation",operation).increment());
  metrics.gauge("emitta.sri.circuit.state",java.util.List.of(io.micrometer.core.instrument.Tag.of("operation",operation)),
      circuit,c -> c.getState().getOrder());
 }
 public <T>T execute(Supplier<T> call) {
  var sample=io.micrometer.core.instrument.Timer.start(metrics);
  try {
   return Retry.decorateSupplier(retry,Bulkhead.decorateSupplier(bulkhead,CircuitBreaker.decorateSupplier(circuit,call))).get();
  } catch(RuntimeException failure) {
   metrics.counter("emitta.sri.failures","operation",operation,"category",
       communicationFailure(failure)?"communication":failure instanceof CallNotPermittedException || failure instanceof BulkheadFullException?"not_started":"validation").increment();
   throw failure;
  } finally { sample.stop(metrics.timer("emitta.sri.duration","operation",operation)); }
 }
 public CircuitBreaker circuit() { return circuit; }
 public Bulkhead bulkhead() { return bulkhead; }
 public static boolean communicationFailure(Throwable failure) {
  for(Throwable t=failure;t!=null;t=t.getCause()) {
   if(t instanceof InterruptedException || t instanceof java.io.InterruptedIOException && !(t instanceof java.net.SocketTimeoutException)) return false;
   if(t instanceof IOException)return true;
  }
  return false;
 }
}
