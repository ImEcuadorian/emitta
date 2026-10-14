package io.github.imecuadorian.emitta.outbox.application.service;

import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;
import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxClaimPort;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxMessagePublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PublishPendingOutboxService
        implements PublishPendingOutboxUseCase {

    private final OutboxClaimPort outboxClaimPort;
    private final OutboxMessagePublisher messagePublisher;

    private final Clock clock;
    private final String workerId;

    private final int batchSize;
    private final Duration claimLease;
    private final Duration initialBackoff, maximumBackoff;
    private final boolean jitter;

    public PublishPendingOutboxService(
            OutboxClaimPort outboxClaimPort,
            OutboxMessagePublisher messagePublisher,
            Clock clock,
            String workerId,
            int batchSize,
            Duration claimLease
    ) {
        this(outboxClaimPort, messagePublisher, clock, workerId, batchSize, claimLease,
                Duration.ofSeconds(1), Duration.ofSeconds(60), false);
    }

    public PublishPendingOutboxService(OutboxClaimPort outboxClaimPort, OutboxMessagePublisher messagePublisher,
            Clock clock, String workerId, int batchSize, Duration claimLease,
            Duration initialBackoff, Duration maximumBackoff, boolean jitter) {
        if(initialBackoff.isNegative() || initialBackoff.isZero() || maximumBackoff.compareTo(initialBackoff)<0)
            throw new IllegalArgumentException("Invalid outbox backoff");
        this.initialBackoff=initialBackoff;this.maximumBackoff=maximumBackoff;this.jitter=jitter;
        this.outboxClaimPort =
                Objects.requireNonNull(
                        outboxClaimPort
                );

        this.messagePublisher =
                Objects.requireNonNull(
                        messagePublisher
                );

        this.clock =
                Objects.requireNonNull(
                        clock
                );

        this.workerId =
                Objects.requireNonNull(
                        workerId
                );

        this.batchSize =
                batchSize;

        this.claimLease =
                Objects.requireNonNull(
                        claimLease
                );
    }

    @Override
    public int publishBatch() {

        Instant now =
                clock.instant();

        List<PendingOutboxEvent> events =
                outboxClaimPort.claimBatch(
                        workerId,
                        batchSize,
                        now.minus(
                                claimLease
                        )
                );

        var failures = messagePublisher.publishBatch(events);
        var confirmed=events.stream().filter(e->!failures.containsKey(e.id())).map(PendingOutboxEvent::id).toList();
        java.util.Set<java.util.UUID> marked;
        try {marked=outboxClaimPort.markPublishedBatch(confirmed,workerId,clock.instant());}
        catch(RuntimeException persistenceFailure) {
            // A commit result may be unknown. Ownership predicates make release safe; replay is at least once.
            marked=java.util.Set.of();
            for(var id:confirmed)failures.put(id,persistenceFailure);
        }
        for(var event:events) if(!marked.contains(event.id())) {
            var failure=failures.getOrDefault(event.id(),new IllegalStateException("Outbox claim ownership changed"));
            outboxClaimPort.releaseAfterFailure(event.id(),workerId,
                clock.instant().plus(retryDelay(event.attempts())),errorMessage(failure));
        }
        return marked.size();
    }
    private Duration retryDelay(int attempt) {
        long delay=Math.min(maximumBackoff.toMillis(),initialBackoff.toMillis()*(1L<<Math.min(Math.max(0,attempt-1),20)));
        if(jitter) delay=Math.min(maximumBackoff.toMillis(),delay+java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1,delay/4+1)));
        return Duration.ofMillis(delay);
    }

    private static String errorMessage(RuntimeException exception) {
        // Do not persist arbitrary transport messages which could include credentials or payloads.
        return exception.getClass().getSimpleName();
    }
}
