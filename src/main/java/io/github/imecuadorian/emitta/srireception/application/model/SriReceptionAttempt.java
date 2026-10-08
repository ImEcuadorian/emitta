package io.github.imecuadorian.emitta.srireception.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SriReceptionAttempt(
        UUID documentId,
        int attemptNumber,
        Instant startedAt
) {

    public SriReceptionAttempt {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                startedAt,
                "Attempt start time cannot be null"
        );

        if (attemptNumber < 1) {

            throw new IllegalArgumentException(
                    "Attempt number must be positive"
            );
        }
    }
}