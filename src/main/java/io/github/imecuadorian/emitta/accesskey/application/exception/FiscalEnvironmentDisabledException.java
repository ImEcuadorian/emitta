package io.github.imecuadorian.emitta.accesskey.application.exception;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.util.UUID;

public final class FiscalEnvironmentDisabledException
        extends RuntimeException {

    private final UUID taxpayerId;
    private final FiscalEnvironment environment;

    public FiscalEnvironmentDisabledException(
            UUID taxpayerId,
            FiscalEnvironment environment
    ) {
        super(
                "Fiscal environment "
                        + environment
                        + " is disabled for taxpayer "
                        + taxpayerId
        );

        this.taxpayerId = taxpayerId;
        this.environment = environment;
    }

    public UUID getTaxpayerId() {
        return taxpayerId;
    }

    public FiscalEnvironment getEnvironment() {
        return environment;
    }
}