package io.github.imecuadorian.emitta.invoicexml.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceXmlSourceData(

        FiscalEnvironment environment,

        String legalName,
        String tradeName,
        String ruc,
        String mainAddress,

        String accessKey,
        long sequential,
        Instant issuedAt,

        String establishmentCode,
        String establishmentAddress,
        String pointOfIssueCode,

        Buyer buyer,

        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String currency,

        List<Item> items,
        List<Payment> payments
) {

    public InvoiceXmlSourceData {

        items = List.copyOf(
                items
        );

        payments = List.copyOf(
                payments
        );
    }

    public record Buyer(
            String identificationType,
            String identification,
            String name,
            String address
    ) {
    }

    public record Item(
            String sku,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal subtotal,
            List<ItemTax> taxes
    ) {

        public Item {

            taxes = List.copyOf(
                    taxes
            );
        }
    }

    public record ItemTax(
            String taxCode,
            String percentageCode,
            BigDecimal rate,
            BigDecimal taxableBase,
            BigDecimal amount
    ) {
    }

    public record Payment(
            String method,
            BigDecimal total,
            BigDecimal term,
            String unitTime
    ) {
    }
}