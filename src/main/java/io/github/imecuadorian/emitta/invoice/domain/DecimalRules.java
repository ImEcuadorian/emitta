package io.github.imecuadorian.emitta.invoice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

final class DecimalRules {

    private static final int MONEY_SCALE =
            2;

    private static final int DETAIL_SCALE =
            6;

    private static final int RATE_SCALE =
            4;

    private DecimalRules() {
    }

    static BigDecimal money(
            BigDecimal value,
            String field
    ) {

        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        BigDecimal normalized =
                value.setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );

        if (normalized.precision() > 14) {
            throw new IllegalArgumentException(
                    field
                            + " exceeds NUMERIC(14,2)"
            );
        }

        return normalized;
    }

    static BigDecimal quantity(
            BigDecimal value
    ) {

        return detailNumber(
                value,
                "Quantity",
                true
        );
    }

    static BigDecimal unitPrice(
            BigDecimal value
    ) {

        return detailNumber(
                value,
                "Unit price",
                false
        );
    }

    static BigDecimal rate(
            BigDecimal value
    ) {

        Objects.requireNonNull(
                value,
                "Tax rate cannot be null"
        );

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Tax rate cannot be negative"
            );
        }

        if (value.scale() > RATE_SCALE) {
            throw new IllegalArgumentException(
                    "Tax rate cannot have more than 4 decimal places"
            );
        }

        if (value.precision() > 7) {
            throw new IllegalArgumentException(
                    "Tax rate exceeds NUMERIC(7,4)"
            );
        }

        return value;
    }

    private static BigDecimal detailNumber(
            BigDecimal value,
            String field,
            boolean strictlyPositive
    ) {

        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        if (
                strictlyPositive
                        ? value.signum() <= 0
                        : value.signum() < 0
        ) {
            throw new IllegalArgumentException(
                    field
                            + (
                            strictlyPositive
                                    ? " must be greater than zero"
                                    : " cannot be negative"
                    )
            );
        }

        if (value.scale() > DETAIL_SCALE) {
            throw new IllegalArgumentException(
                    field
                            + " cannot have more than 6 decimal places"
            );
        }

        if (value.precision() > 18) {
            throw new IllegalArgumentException(
                    field
                            + " exceeds NUMERIC(18,6)"
            );
        }

        return value;
    }
}