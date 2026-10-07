package io.github.imecuadorian.emitta.invoicexml.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceXmlData(

        String environmentCode,

        String legalName,
        String tradeName,
        String ruc,

        String accessKey,

        String establishmentCode,
        String pointOfIssueCode,
        String sequential,

        String mainAddress,

        LocalDate issueDate,
        String establishmentAddress,

        Buyer buyer,

        BigDecimal totalWithoutTaxes,
        BigDecimal discountTotal,

        List<TaxTotal> taxTotals,

        BigDecimal tip,
        BigDecimal total,
        String currency,

        List<Item> items,
        List<Payment> payments,

        String providerRuc
) {

    public InvoiceXmlData {

        taxTotals = List.copyOf(
                taxTotals
        );

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

    public record TaxTotal(
            String taxCode,
            String percentageCode,
            BigDecimal taxableBase,
            BigDecimal amount
    ) {
    }

    public record Item(
            String code,
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