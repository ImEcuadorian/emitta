package io.github.imecuadorian.emitta.invoice.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record InvoiceTaxSpec(
        String taxCode,
        String percentageCode,
        BigDecimal rate
) {

    public InvoiceTaxSpec {

        taxCode =
                numericCode(
                        taxCode,
                        "Tax code"
                );

        percentageCode =
                numericCode(
                        percentageCode,
                        "Percentage code"
                );

        rate =
                DecimalRules.rate(
                        rate
                );
    }

    private static String numericCode(
            String value,
            String field
    ) {

        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        String normalized =
                value.trim();

        if (
                normalized.isEmpty()
                        || normalized.length() > 4
                        || !normalized.matches("\\d+")
        ) {
            throw new IllegalArgumentException(
                    field
                            + " must contain between 1 and 4 digits"
            );
        }

        return normalized;
    }
}