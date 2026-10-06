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

    public PublishPendingOutboxService(
            OutboxClaimPort outboxClaimPort,
            OutboxMessagePublisher messagePublisher,
            Clock clock,
            String workerId,
            int batchSize,
            Duration claimLease
    ) {
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

        int published =
                0;

        for (PendingOutboxEvent event : events) {

            try {

                messagePublisher.publish(
                        event
                );

                boolean marked =
                        outboxClaimPort.markPublished(
                                event.id(),
                                workerId,
                                clock.instant()
                        );

                if (!marked) {
                    throw new IllegalStateException(
                            "Published event could not be marked as published: "
                                    + event.id()
                    );
                }

                published++;

            } catch (RuntimeException exception) {

                Instant nextAttemptAt =
                        clock.instant()
                                .plus(
                                        retryDelay(
                                                event.attempts()
                                        )
                                );

                outboxClaimPort.releaseAfterFailure(
                        event.id(),
                        workerId,
                        nextAttemptAt,
                        errorMessage(
                                exception
                        )
                );
            }
        }

        return published;
    }

    private static Duration retryDelay(
            int attempt
    ) {

        long seconds =
                Math.min(
                        60L,
                        1L << Math.min(
                                attempt - 1,
                                6
                        )
                );

        return Duration.ofSeconds(
                seconds
        );
    }

    private static String errorMessage(
            RuntimeException exception
    ) {

        String message =
                exception.getMessage();

        if (message == null) {
            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message.length() <= 2000
                ? message
                : message.substring(
                0,
                2000
        );
    }
}