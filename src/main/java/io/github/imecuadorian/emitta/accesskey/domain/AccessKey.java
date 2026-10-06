package io.github.imecuadorian.emitta.accesskey.domain;

import java.util.Objects;

public record AccessKey(String value) {

    public AccessKey {

        Objects.requireNonNull(
                value,
                "Access key cannot be null"
        );

        value = value.trim();

        if (!value.matches("\\d{49}")) {
            throw new IllegalArgumentException(
                    "Access key must contain exactly 49 numeric digits"
            );
        }

        String base =
                value.substring(
                        0,
                        48
                );

        int expectedCheckDigit =
                Modulus11.calculate(base);

        int actualCheckDigit =
                Character.digit(
                        value.charAt(48),
                        10
                );

        if (
                expectedCheckDigit
                        != actualCheckDigit
        ) {
            throw new IllegalArgumentException(
                    "Invalid access key check digit"
            );
        }
    }

    @Override
    public String toString() {
        return value;
    }
}