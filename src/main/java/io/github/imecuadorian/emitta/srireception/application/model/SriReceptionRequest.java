package io.github.imecuadorian.emitta.srireception.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.util.Arrays;
import java.util.Objects;

public record SriReceptionRequest(
        FiscalEnvironment environment,
        byte[] signedXml
) {

    public SriReceptionRequest {

        Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );

        if (
                signedXml == null
                        || signedXml.length == 0
        ) {

            throw new IllegalArgumentException(
                    "Signed XML cannot be empty"
            );
        }

        signedXml =
                Arrays.copyOf(
                        signedXml,
                        signedXml.length
                );
    }

    @Override
    public byte[] signedXml() {

        return Arrays.copyOf(
                signedXml,
                signedXml.length
        );
    }
}