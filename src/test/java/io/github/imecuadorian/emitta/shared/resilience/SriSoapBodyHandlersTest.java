package io.github.imecuadorian.emitta.shared.resilience;
import org.junit.jupiter.api.Test;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SriSoapBodyHandlersTest {
 @Test void readTimeoutCancelsTheActualSubscription() throws Exception {
  var subscriber=SriSoapBodyHandlers.timedBytes(Duration.ofMillis(50)).apply(null);
  var transport=mock(Flow.Subscription.class);subscriber.onSubscribe(transport);
  var failure=assertThrows(ExecutionException.class,()->subscriber.getBody().toCompletableFuture().get(2,TimeUnit.SECONDS));
  assertInstanceOf(HttpTimeoutException.class,failure.getCause());verify(transport,timeout(1000)).cancel();
 }
 @Test void completeBodyRemainsIntactAndDoesNotCancelTransport() throws Exception {
  var subscriber=SriSoapBodyHandlers.timedBytes(Duration.ofSeconds(2)).apply(null);
  var transport=mock(Flow.Subscription.class);subscriber.onSubscribe(transport);
  subscriber.onNext(List.of(ByteBuffer.wrap("synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8))));subscriber.onComplete();
  assertEquals("synthetic",new String(subscriber.getBody().toCompletableFuture().get(1,TimeUnit.SECONDS),java.nio.charset.StandardCharsets.UTF_8));verify(transport,never()).cancel();
 }
}
