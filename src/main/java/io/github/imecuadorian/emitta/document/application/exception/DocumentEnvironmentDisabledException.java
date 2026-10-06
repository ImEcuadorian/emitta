package io.github.imecuadorian.emitta.document.application.exception;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.util.UUID;

public final class DocumentEnvironmentDisabledException
        extends RuntimeException {

    public DocumentEnvironmentDisabledException(
            UUID taxpayerId,
            FiscalEnvironment environment
    ) {
        super(
                "Environment "
                        + environment
                        + " is disabled for taxpayer "
                        + taxpayerId
        );
    }
}