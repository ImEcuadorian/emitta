package io.github.imecuadorian.emitta.sriauthorization.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SriAuthorizationEvidence(
        UUID documentId,
        String authorizationNumber,
        Instant authorizedAt,
        FiscalEnvironment environment,
        String authorizedXmlSha256,
        List<SriAuthorizationMessage> messages
) {

    public SriAuthorizationEvidence {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                authorizedAt,
                "Authorization date cannot be null"
        );

        Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );

        if (authorizationNumber == null
                || !authorizationNumber.matches("[0-9]{49}")) {

            throw new IllegalArgumentException(
                    "Authorization number must contain 49 digits"
            );
        }

        if (authorizedXmlSha256 == null
                || !authorizedXmlSha256.matches("[0-9a-f]{64}")) {

            throw new IllegalArgumentException(
                    "Authorized XML SHA-256 must contain 64 hexadecimal characters"
            );
        }

        messages = List.copyOf(
                Objects.requireNonNull(
                        messages,
                        "Authorization messages cannot be null"
                )
        );
    }
}