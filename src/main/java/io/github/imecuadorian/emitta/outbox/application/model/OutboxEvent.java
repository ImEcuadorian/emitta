package io.github.imecuadorian.emitta.outbox.application.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload
) {

    public OutboxEvent {

        Objects.requireNonNull(
                id,
                "Outbox event id cannot be null"
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
                payload,
                "Outbox payload cannot be null"
        );

        aggregateType =
                requireNotBlank(
                        aggregateType,
                        "Aggregate type"
                );

        eventType =
                requireNotBlank(
                        eventType,
                        "Event type"
                );

        payload =
                Map.copyOf(payload);
    }

    private static String requireNotBlank(
            String value,
            String field
    ) {

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return normalized;
    }
}