package io.github.imecuadorian.emitta.fiscalprocessing.application.exception;

import java.util.UUID;

public final class FiscalDocumentNotFoundException
        extends RuntimeException {

    private final UUID documentId;

    public FiscalDocumentNotFoundException(
            UUID documentId
    ) {
        super(
                "Fiscal document not found: "
                        + documentId
        );

        this.documentId =
                documentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}