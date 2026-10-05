package io.github.imecuadorian.emitta.taxpayer.domain;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record Ruc(String value) {

    private static final int RUC_LENGTH = 13;

    public Ruc {
        Objects.requireNonNull(
                value,
                "RUC cannot be null"
        );

        value = value.trim();

        if (!value.matches("\\d{" + RUC_LENGTH + "}")) {
            throw new IllegalArgumentException(
                    "RUC must contain exactly 13 numeric digits"
            );
        }
    }

    @Override
    public @NonNull String toString() {
        return value;
    }
}