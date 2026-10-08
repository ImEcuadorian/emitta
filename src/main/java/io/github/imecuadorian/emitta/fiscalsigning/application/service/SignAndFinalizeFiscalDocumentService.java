package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.MarkDocumentSignedUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignAndFinalizeFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignFiscalDocumentUseCase;

import java.util.Objects;
import java.util.UUID;

public final class SignAndFinalizeFiscalDocumentService
        implements SignAndFinalizeFiscalDocumentUseCase {

    private final SignFiscalDocumentUseCase
            signFiscalDocumentUseCase;

    private final MarkDocumentSignedUseCase
            markDocumentSignedUseCase;

    public SignAndFinalizeFiscalDocumentService(
            SignFiscalDocumentUseCase signFiscalDocumentUseCase,
            MarkDocumentSignedUseCase markDocumentSignedUseCase
    ) {

        this.signFiscalDocumentUseCase =
                Objects.requireNonNull(
                        signFiscalDocumentUseCase
                );

        this.markDocumentSignedUseCase =
                Objects.requireNonNull(
                        markDocumentSignedUseCase
                );
    }

    @Override
    public Document signAndFinalize(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        /*
         * The signed artifact must be durably stored before
         * the document can transition to SIGNED.
         */
        signFiscalDocumentUseCase.sign(
                documentId
        );

        return markDocumentSignedUseCase.markSigned(
                documentId
        );
    }
}