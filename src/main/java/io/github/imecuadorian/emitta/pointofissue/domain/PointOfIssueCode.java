package io.github.imecuadorian.emitta.pointofissue.domain;

import java.util.Objects;

public record PointOfIssueCode(String value) {

    public PointOfIssueCode {

        Objects.requireNonNull(
                value,
                "Point of issue code cannot be null"
        );

        value = value.trim();

        if (!value.matches("\\d{3}")) {
            throw new IllegalArgumentException(
                    "Point of issue code must contain exactly 3 numeric digits"
            );
        }
    }

    @Override
    public String toString() {
        return value;
    }
}