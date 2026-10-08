package io.github.imecuadorian.emitta.sriauthorization.application.model;

import java.util.Objects;

public record SriAuthorizationMessage(
        String identifier,
        String message,
        String additionalInformation,
        String type
) {

    public SriAuthorizationMessage {

        identifier = requireText(
                identifier,
                "Message identifier"
        );

        message = requireText(
                message,
                "Message description"
        );

        type = requireText(
                type,
                "Message type"
        );

        if (additionalInformation != null) {
            additionalInformation =
                    additionalInformation.trim();
        }
    }

    private static String requireText(
            String value,
            String field
    ) {

        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }
}