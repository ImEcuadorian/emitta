package io.github.imecuadorian.emitta.fiscalprocessing.application.model;

import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import java.util.Objects;
import java.util.UUID;

public record ProcessFiscalDocumentResult(
        UUID documentId,
        DocumentStatus status,
        boolean processed
) {

    public ProcessFiscalDocumentResult {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                status,
                "Document status cannot be null"
        );
    }
}