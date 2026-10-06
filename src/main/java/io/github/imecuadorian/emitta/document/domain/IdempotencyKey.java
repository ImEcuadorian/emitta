package io.github.imecuadorian.emitta.document.domain;

import java.util.Objects;

public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 255;

    public IdempotencyKey {

        Objects.requireNonNull(
                value,
                "Idempotency key cannot be null"
        );

        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Idempotency key cannot be blank"
            );
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency key cannot exceed 255 characters"
            );
        }
    }

    @Override
    public String toString() {
        return value;
    }
}