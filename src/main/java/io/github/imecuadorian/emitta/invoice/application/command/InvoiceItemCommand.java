package io.github.imecuadorian.emitta.invoice.application.command;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceItemCommand(
        String sku,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        List<InvoiceTaxCommand> taxes
) {

    public InvoiceItemCommand {
        taxes =
                taxes == null
                        ? List.of()
                        : List.copyOf(taxes);
    }
}