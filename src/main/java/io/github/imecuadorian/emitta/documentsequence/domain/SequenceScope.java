package io.github.imecuadorian.emitta.documentsequence.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.util.Objects;
import java.util.UUID;

public record SequenceScope(
        UUID pointOfIssueId,
        DocumentType documentType,
        FiscalEnvironment environment
) {

    public SequenceScope {

        Objects.requireNonNull(
                pointOfIssueId,
                "Point of issue id cannot be null"
        );

        Objects.requireNonNull(
                documentType,
                "Document type cannot be null"
        );

        Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );
    }
}