package io.github.imecuadorian.emitta.shared.resilience;

import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Java 25 request timeout ends at response headers. This also bounds body consumption. */
public final class SriSoapBodyHandlers {
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "emitta-soap-body-deadlines");
        thread.setDaemon(true);
        return thread;
    });

    public static HttpResponse.BodyHandler<byte[]> timedBytes(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive SOAP read timeout required");
        return response -> new TimedBytes(timeout);
    }

    private static final class TimedBytes implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final ScheduledFuture<?> deadline;

        private TimedBytes(Duration timeout) {
            deadline = DEADLINES.schedule(() -> {
                if (body.completeExceptionally(new HttpTimeoutException("SRI SOAP response body timed out"))) {
                    var active = subscription.get();
                    if (active != null) active.cancel(); // cancel actual transport subscription, not just a waiting thread
                }
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
            delegate.getBody().whenComplete((bytes, failure) -> {
                deadline.cancel(false);
                if (failure == null) body.complete(bytes);
                else body.completeExceptionally(failure);
            });
        }

        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription active) {
            subscription.set(active);
            if (body.isCompletedExceptionally()) active.cancel();
            else delegate.onSubscribe(active);
        }
        @Override public void onNext(List<ByteBuffer> bytes) { delegate.onNext(bytes); }
        @Override public void onError(Throwable failure) { delegate.onError(failure); }
        @Override public void onComplete() { delegate.onComplete(); }
    }

    private SriSoapBodyHandlers() {}
}
