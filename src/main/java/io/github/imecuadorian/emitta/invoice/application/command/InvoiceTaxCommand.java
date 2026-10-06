package io.github.imecuadorian.emitta.invoice.application.command;

import java.math.BigDecimal;

public record InvoiceTaxCommand(
        String taxCode,
        String percentageCode,
        BigDecimal rate
) {
}