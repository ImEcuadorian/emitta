package io.github.imecuadorian.emitta.invoice.domain;

import java.util.Objects;

public record BuyerSnapshot(
        String identificationType,
        String identification,
        String name,
        String email,
        String address
) {

    public BuyerSnapshot {

        identificationType =
                require(
                        identificationType,
                        "Buyer identification type",
                        30
                );

        identification =
                require(
                        identification,
                        "Buyer identification",
                        30
                );

        name =
                require(
                        name,
                        "Buyer name",
                        300
                );

        email =
                optional(
                        email,
                        320,
                        "Buyer email"
                );

        address =
                optional(
                        address,
                        500,
                        "Buyer address"
                );
    }

    private static String require(
            String value,
            String field,
            int maxLength
    ) {

        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " exceeds " + maxLength + " characters"
            );
        }

        return normalized;
    }

    private static String optional(
            String value,
            int maxLength,
            String field
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " exceeds " + maxLength + " characters"
            );
        }

        return normalized;
    }
}