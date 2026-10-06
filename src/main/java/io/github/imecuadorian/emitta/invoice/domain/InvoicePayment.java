package io.github.imecuadorian.emitta.invoice.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record InvoicePayment(
        UUID id,
        int lineNumber,
        String paymentMethod,
        BigDecimal total,
        BigDecimal term,
        String unitTime
) {

    public InvoicePayment {

        Objects.requireNonNull(
                id,
                "Payment id cannot be null"
        );

        if (lineNumber <= 0) {
            throw new IllegalArgumentException(
                    "Payment line number must be greater than zero"
            );
        }

        Objects.requireNonNull(
                paymentMethod,
                "Payment method cannot be null"
        );

        paymentMethod =
                paymentMethod.trim();

        if (!paymentMethod.matches("\\d{2}")) {
            throw new IllegalArgumentException(
                    "Payment method must contain exactly 2 digits"
            );
        }

        total =
                DecimalRules.money(
                        total,
                        "Payment total"
                );

        if (total.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment total must be greater than zero"
            );
        }

        if (term == null) {

            if (unitTime != null) {
                throw new IllegalArgumentException(
                        "Unit time requires payment term"
                );
            }

        } else {

            term =
                    DecimalRules.money(
                            term,
                            "Payment term"
                    );

            if (term.signum() < 0) {
                throw new IllegalArgumentException(
                        "Payment term cannot be negative"
                );
            }

            Objects.requireNonNull(
                    unitTime,
                    "Unit time cannot be null when term is present"
            );

            unitTime =
                    unitTime.trim();

            if (
                    unitTime.isEmpty()
                            || unitTime.length() > 10
            ) {
                throw new IllegalArgumentException(
                        "Unit time must contain between 1 and 10 characters"
                );
            }
        }
    }
}