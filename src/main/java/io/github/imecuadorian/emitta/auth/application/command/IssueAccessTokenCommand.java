package io.github.imecuadorian.emitta.auth.application.command;

import java.util.Objects;

public record IssueAccessTokenCommand(
        String clientId,
        String clientSecret
) {

    public IssueAccessTokenCommand {

        clientId =
                require(
                        clientId,
                        "Client id"
                );

        clientSecret =
                require(
                        clientSecret,
                        "Client secret"
                );
    }

    private static String require(
            String value,
            String field
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

        return normalized;
    }
}