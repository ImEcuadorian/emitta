package io.github.imecuadorian.emitta.accesskey.application.model;

import io.github.imecuadorian.emitta.accesskey.domain.AccessKey;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;

import java.util.Objects;

public record GeneratedAccessKey(
        AccessKey accessKey,
        SequentialNumber sequential,
        String numericCode
) {

    public GeneratedAccessKey {

        Objects.requireNonNull(
                accessKey,
                "Access key cannot be null"
        );

        Objects.requireNonNull(
                sequential,
                "Sequential cannot be null"
        );

        Objects.requireNonNull(
                numericCode,
                "Numeric code cannot be null"
        );

        if (!numericCode.matches("\\d{8}")) {
            throw new IllegalArgumentException(
                    "Numeric code must contain exactly 8 digits"
            );
        }
    }
}