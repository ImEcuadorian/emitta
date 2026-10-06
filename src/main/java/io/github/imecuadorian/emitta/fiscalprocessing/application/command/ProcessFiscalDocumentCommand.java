package io.github.imecuadorian.emitta.fiscalprocessing.application.command;

import java.util.Objects;
import java.util.UUID;

public record ProcessFiscalDocumentCommand(
        UUID documentId
) {

    public ProcessFiscalDocumentCommand {
        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );
    }
}