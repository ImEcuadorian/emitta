package io.github.imecuadorian.emitta.sriauthorization.application.model;

import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SriAuthorizationResult(
        SriAuthorizationStatus status,
        String authorizationNumber,
        Instant authorizedAt,
        String authorizedXml,
        List<SriAuthorizationMessage> messages
) {

    public SriAuthorizationResult {

        Objects.requireNonNull(
                status,
                "Authorization status cannot be null"
        );

        messages = List.copyOf(
                Objects.requireNonNull(
                        messages,
                        "Authorization messages cannot be null"
                )
        );

        if (
                status == SriAuthorizationStatus.AUTHORIZED
                        && (
                        authorizationNumber == null
                                || authorizationNumber.isBlank()
                )
        ) {

            throw new IllegalArgumentException(
                    "Authorized document must have an authorization number"
            );
        }

        if (
                status == SriAuthorizationStatus.NOT_FOUND
                        && !messages.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Not found response cannot contain authorization messages"
            );
        }
    }
}