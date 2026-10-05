package io.github.imecuadorian.emitta.taxpayer.application.exception;

import lombok.Getter;

@Getter
public final class TaxpayerAlreadyExistsException
        extends RuntimeException {

    private final String ruc;

    public TaxpayerAlreadyExistsException(
            String ruc
    ) {
        super(
                "Taxpayer already exists for RUC: "
                        + ruc
        );

        this.ruc = ruc;
    }

}