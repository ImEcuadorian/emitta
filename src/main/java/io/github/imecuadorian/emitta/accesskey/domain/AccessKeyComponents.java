package io.github.imecuadorian.emitta.accesskey.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.time.LocalDate;
import java.util.Objects;

public record AccessKeyComponents(
        LocalDate issueDate,
        DocumentType documentType,
        String ruc,
        FiscalEnvironment environment,
        String establishmentCode,
        String pointOfIssueCode,
        String sequential,
        String numericCode
) {

    public AccessKeyComponents {

        Objects.requireNonNull(
                issueDate,
                "Issue date cannot be null"
        );

        Objects.requireNonNull(
                documentType,
                "Document type cannot be null"
        );

        Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );

        ruc = requireNumeric(
                ruc,
                13,
                "RUC"
        );

        establishmentCode = requireNumeric(
                establishmentCode,
                3,
                "Establishment code"
        );

        pointOfIssueCode = requireNumeric(
                pointOfIssueCode,
                3,
                "Point of issue code"
        );

        sequential = requireNumeric(
                sequential,
                9,
                "Sequential"
        );

        numericCode = requireNumeric(
                numericCode,
                8,
                "Numeric code"
        );
    }

    private static String requireNumeric(
            String value,
            int length,
            String field
    ) {
        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        String normalized =
                value.trim();

        if (
                !normalized.matches(
                        "\\d{" + length + "}"
                )
        ) {
            throw new IllegalArgumentException(
                    field
                            + " must contain exactly "
                            + length
                            + " numeric digits"
            );
        }

        return normalized;
    }
}