package io.github.imecuadorian.emitta.documentsequence.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class DocumentSequence {

    private static final long MAX_VALUE = 999_999_999L;

    private final UUID id;
    private final UUID pointOfIssueId;
    private final DocumentType documentType;
    private final FiscalEnvironment environment;

    private long currentValue;
    private Instant updatedAt;

    private DocumentSequence(
            UUID id,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            long currentValue,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(
                id,
                "Document sequence id cannot be null"
        );

        this.pointOfIssueId = Objects.requireNonNull(
                pointOfIssueId,
                "Point of issue id cannot be null"
        );

        this.documentType = Objects.requireNonNull(
                documentType,
                "Document type cannot be null"
        );

        this.environment = Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );

        validateCurrentValue(currentValue);

        this.currentValue = currentValue;

        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Update time cannot be null"
        );
    }

    public static DocumentSequence create(
            UUID id,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            Instant now
    ) {
        Objects.requireNonNull(
                now,
                "Creation time cannot be null"
        );

        return new DocumentSequence(
                id,
                pointOfIssueId,
                documentType,
                environment,
                0L,
                now
        );
    }

    public static DocumentSequence restore(
            UUID id,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            long currentValue,
            Instant updatedAt
    ) {
        return new DocumentSequence(
                id,
                pointOfIssueId,
                documentType,
                environment,
                currentValue,
                updatedAt
        );
    }

    private static void validateCurrentValue(
            long currentValue
    ) {
        if (
                currentValue < 0
                        || currentValue > MAX_VALUE
        ) {
            throw new IllegalArgumentException(
                    "Current sequence value must be between 0 and 999999999"
            );
        }
    }

}