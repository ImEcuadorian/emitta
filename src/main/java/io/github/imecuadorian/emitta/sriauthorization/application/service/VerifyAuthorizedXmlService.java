package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.VerifyAuthorizedXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public final class VerifyAuthorizedXmlService
        implements VerifyAuthorizedXmlUseCase {

    @Override
    public void verify(
            byte[] originalSignedXml,
            SriAuthorizationResult authorization
    ) {

        Objects.requireNonNull(
                originalSignedXml,
                "Original signed XML cannot be null"
        );

        Objects.requireNonNull(
                authorization,
                "SRI authorization cannot be null"
        );

        if (originalSignedXml.length == 0) {

            throw new IllegalArgumentException(
                    "Original signed XML cannot be empty"
            );
        }

        if (
                authorization.status()
                        != SriAuthorizationStatus.AUTHORIZED
        ) {

            throw new IllegalStateException(
                    "Cannot verify authorized XML from SRI status "
                            + authorization.status()
            );
        }

        String returnedXml =
                authorization.authorizedXml();

        if (returnedXml == null || returnedXml.isBlank()) {

            throw new IllegalStateException(
                    "SRI authorized XML is missing"
            );
        }

        byte[] returnedBytes =
                returnedXml.getBytes(
                        StandardCharsets.UTF_8
                );

        /*
         * Conservative verification:
         * the XML returned by SRI must be byte-identical
         * to the signed XML previously persisted by Emitta.
         *
         * This does not replace XMLDSig validation.
         */
        if (
                !MessageDigest.isEqual(
                        originalSignedXml,
                        returnedBytes
                )
        ) {

            throw new IllegalStateException(
                    "SRI authorized XML differs from the original signed XML"
            );
        }
    }
}