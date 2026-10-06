package io.github.imecuadorian.emitta.establishment.application.exception;

import java.util.UUID;

public final class TaxpayerNotFoundException
        extends RuntimeException {

    private final UUID taxpayerId;

    public TaxpayerNotFoundException(
            UUID taxpayerId
    ) {
        super(
                "Taxpayer not found: "
                        + taxpayerId
        );

        this.taxpayerId = taxpayerId;
    }

    public UUID getTaxpayerId() {
        return taxpayerId;
    }
}