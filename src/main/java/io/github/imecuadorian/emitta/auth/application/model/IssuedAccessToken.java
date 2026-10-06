package io.github.imecuadorian.emitta.auth.application.model;

import java.util.Objects;

public record IssuedAccessToken(
        String token,
        long expiresInSeconds
) {

    public IssuedAccessToken {

        Objects.requireNonNull(
                token,
                "Access token cannot be null"
        );

        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Token lifetime must be positive"
            );
        }
    }
}