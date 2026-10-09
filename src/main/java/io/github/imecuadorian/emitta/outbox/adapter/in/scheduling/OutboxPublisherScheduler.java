package io.github.imecuadorian.emitta.outbox.adapter.in.scheduling;

import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "emitta.outbox.publisher",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public final class OutboxPublisherScheduler {

    private static final int MAX_BATCHES_PER_RUN =
            10;

    private final PublishPendingOutboxUseCase
            publishPendingOutboxUseCase;

    public OutboxPublisherScheduler(
            PublishPendingOutboxUseCase publishPendingOutboxUseCase
    ) {
        this.publishPendingOutboxUseCase =
                Objects.requireNonNull(
                        publishPendingOutboxUseCase
                );
    }

    @Scheduled(
            fixedDelayString =
                    "${emitta.outbox.publisher.fixed-delay-ms:250}",
            initialDelayString =
                    "${emitta.outbox.publisher.initial-delay-ms:1000}"
    )
    public void publishPending() {

        for (
                int batch = 0;
                batch < MAX_BATCHES_PER_RUN;
                batch++
        ) {

            int published =
                    publishPendingOutboxUseCase
                            .publishBatch();

            if (published == 0) {
                return;
            }
        }
    }
}