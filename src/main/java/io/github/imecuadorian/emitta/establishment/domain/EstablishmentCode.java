package io.github.imecuadorian.emitta.establishment.domain;

import java.util.Objects;

public record EstablishmentCode(String value) {

    public EstablishmentCode {

        Objects.requireNonNull(
                value,
                "Establishment code cannot be null"
        );

        value = value.trim();

        if (!value.matches("\\d{3}")) {
            throw new IllegalArgumentException(
                    "Establishment code must contain exactly 3 numeric digits"
            );
        }
    }

    @Override
    public String toString() {
        return value;
    }
}