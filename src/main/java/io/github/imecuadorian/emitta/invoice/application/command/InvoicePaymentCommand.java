package io.github.imecuadorian.emitta.invoice.application.command;

import java.math.BigDecimal;

public record InvoicePaymentCommand(
        String paymentMethod,
        BigDecimal total,
        BigDecimal term,
        String unitTime
) {
}