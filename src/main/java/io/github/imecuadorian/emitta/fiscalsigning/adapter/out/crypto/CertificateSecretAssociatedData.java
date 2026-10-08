package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto;

import java.util.Objects;
import java.util.UUID;

public final class CertificateSecretAssociatedData {

    private CertificateSecretAssociatedData() {
    }

    public static String content(
            UUID taxpayerId,
            UUID certificateId
    ) {

        return build(
                taxpayerId,
                certificateId,
                "content"
        );
    }

    public static String password(
            UUID taxpayerId,
            UUID certificateId
    ) {

        return build(
                taxpayerId,
                certificateId,
                "password"
        );
    }

    private static String build(
            UUID taxpayerId,
            UUID certificateId,
            String purpose
    ) {

        Objects.requireNonNull(
                taxpayerId,
                "Taxpayer id cannot be null"
        );

        Objects.requireNonNull(
                certificateId,
                "Certificate id cannot be null"
        );

        return "emitta:certificate:v1:"
                + taxpayerId
                + ":"
                + certificateId
                + ":"
                + purpose;
    }
}