package io.github.imecuadorian.emitta.outbox.application.model;

import java.util.Objects;
import java.util.UUID;

public record PendingOutboxEvent(
        UUID id,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payloadJson,
        int attempts
) {

    public PendingOutboxEvent {

        Objects.requireNonNull(
                id,
                "Event id cannot be null"
        );

        Objects.requireNonNull(
                aggregateType,
                "Aggregate type cannot be null"
        );

        Objects.requireNonNull(
                aggregateId,
                "Aggregate id cannot be null"
        );

        Objects.requireNonNull(
                eventType,
                "Event type cannot be null"
        );

        Objects.requireNonNull(
                payloadJson,
                "Payload cannot be null"
        );

        if (attempts < 1) {
            throw new IllegalArgumentException(
                    "Attempts must be at least 1 for a claimed event"
            );
        }
    }
}