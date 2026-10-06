package io.github.imecuadorian.emitta.invoice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public record InvoiceItemTax(
        UUID id,
        String taxCode,
        String percentageCode,
        BigDecimal rate,
        BigDecimal taxableBase,
        BigDecimal taxAmount
) {

    public InvoiceItemTax {

        Objects.requireNonNull(
                id,
                "Tax id cannot be null"
        );

        Objects.requireNonNull(
                taxCode,
                "Tax code cannot be null"
        );

        Objects.requireNonNull(
                percentageCode,
                "Percentage code cannot be null"
        );

        rate =
                DecimalRules.rate(
                        rate
                );

        taxableBase =
                DecimalRules.money(
                        taxableBase,
                        "Taxable base"
                );

        taxAmount =
                DecimalRules.money(
                        taxAmount,
                        "Tax amount"
                );
    }

    public static InvoiceItemTax calculate(
            UUID id,
            InvoiceTaxSpec specification,
            BigDecimal taxableBase
    ) {

        Objects.requireNonNull(
                specification,
                "Tax specification cannot be null"
        );

        BigDecimal normalizedBase =
                DecimalRules.money(
                        taxableBase,
                        "Taxable base"
                );

        BigDecimal amount =
                normalizedBase
                        .multiply(
                                specification.rate()
                        )
                        .divide(
                                BigDecimal.valueOf(100),
                                8,
                                RoundingMode.HALF_UP
                        );

        return new InvoiceItemTax(
                id,
                specification.taxCode(),
                specification.percentageCode(),
                specification.rate(),
                normalizedBase,
                DecimalRules.money(
                        amount,
                        "Tax amount"
                )
        );
    }
}