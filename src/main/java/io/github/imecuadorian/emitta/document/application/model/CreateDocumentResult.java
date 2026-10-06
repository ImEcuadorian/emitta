package io.github.imecuadorian.emitta.document.application.model;

import io.github.imecuadorian.emitta.document.domain.Document;

import java.util.Objects;

public record CreateDocumentResult(
        Document document,
        boolean created
) {

    public CreateDocumentResult {
        Objects.requireNonNull(
                document,
                "Document cannot be null"
        );
    }
}