package io.github.imecuadorian.emitta.sriauthorization.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.util.Objects;

public record SriAuthorizationRequest(
        FiscalEnvironment environment,
        String accessKey
) {

    public SriAuthorizationRequest {

        Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );

        Objects.requireNonNull(
                accessKey,
                "Access key cannot be null"
        );

        if (!accessKey.matches("[0-9]{49}")) {

            throw new IllegalArgumentException(
                    "Access key must contain exactly 49 digits"
            );
        }
    }
}