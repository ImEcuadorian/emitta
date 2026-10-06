package io.github.imecuadorian.emitta.invoice.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Getter
public final class InvoiceItem {

    private final UUID id;

    private final int lineNumber;

    private final String sku;
    private final String description;

    private final BigDecimal quantity;
    private final BigDecimal unitPrice;

    private final BigDecimal discount;
    private final BigDecimal subtotal;
    private final BigDecimal taxTotal;
    private final BigDecimal total;

    private final List<InvoiceItemTax> taxes;

    public static InvoiceItem restore(
            UUID id,
            int lineNumber,
            String sku,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal total,
            List<InvoiceItemTax> taxes
    ) {

        return new InvoiceItem(
                id,
                lineNumber,
                sku,
                description,
                quantity,
                unitPrice,
                discount,
                subtotal,
                taxTotal,
                total,
                taxes
        );
    }

    private InvoiceItem(
            UUID id,
            int lineNumber,
            String sku,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal total,
            List<InvoiceItemTax> taxes
    ) {
        this.id = id;
        this.lineNumber = lineNumber;
        this.sku = sku;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discount = discount;
        this.subtotal = subtotal;
        this.taxTotal = taxTotal;
        this.total = total;
        this.taxes = List.copyOf(taxes);
    }

    public static InvoiceItem create(
            UUID id,
            int lineNumber,
            String sku,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            List<InvoiceTaxSpec> taxSpecifications,
            Supplier<UUID> idGenerator
    ) {

        Objects.requireNonNull(
                id,
                "Invoice item id cannot be null"
        );

        Objects.requireNonNull(
                idGenerator,
                "Id generator cannot be null"
        );

        if (lineNumber <= 0) {
            throw new IllegalArgumentException(
                    "Line number must be greater than zero"
            );
        }

        String normalizedSku =
                normalizeOptional(
                        sku,
                        25,
                        "SKU"
                );

        String normalizedDescription =
                normalizeRequired(
                        description,
                        300,
                        "Description"
                );

        BigDecimal normalizedQuantity =
                DecimalRules.quantity(
                        quantity
                );

        BigDecimal normalizedUnitPrice =
                DecimalRules.unitPrice(
                        unitPrice
                );

        BigDecimal normalizedDiscount =
                DecimalRules.money(
                        discount,
                        "Discount"
                );

        BigDecimal gross =
                DecimalRules.money(
                        normalizedQuantity.multiply(
                                normalizedUnitPrice
                        ),
                        "Gross amount"
                );

        if (
                normalizedDiscount.compareTo(
                        gross
                ) > 0
        ) {
            throw new IllegalArgumentException(
                    "Discount cannot exceed gross amount"
            );
        }

        BigDecimal subtotal =
                DecimalRules.money(
                        gross.subtract(
                                normalizedDiscount
                        ),
                        "Subtotal"
                );

        if (
                taxSpecifications == null
                        || taxSpecifications.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Invoice item must contain at least one tax treatment"
            );
        }

        List<InvoiceItemTax> taxes =
                new ArrayList<>();

        for (
                InvoiceTaxSpec specification
                : taxSpecifications
        ) {

            taxes.add(
                    InvoiceItemTax.calculate(
                            idGenerator.get(),
                            specification,
                            subtotal
                    )
            );
        }

        BigDecimal taxTotal =
                taxes.stream()
                        .map(
                                InvoiceItemTax::taxAmount
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        taxTotal =
                DecimalRules.money(
                        taxTotal,
                        "Item tax total"
                );

        BigDecimal total =
                DecimalRules.money(
                        subtotal.add(
                                taxTotal
                        ),
                        "Item total"
                );

        return new InvoiceItem(
                id,
                lineNumber,
                normalizedSku,
                normalizedDescription,
                normalizedQuantity,
                normalizedUnitPrice,
                normalizedDiscount,
                subtotal,
                taxTotal,
                total,
                taxes
        );
    }

    private static String normalizeRequired(
            String value,
            int maximum,
            String field
    ) {

        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    field + " exceeds " + maximum + " characters"
            );
        }

        return normalized;
    }

    private static String normalizeOptional(
            String value,
            int maximum,
            String field
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    field + " exceeds " + maximum + " characters"
            );
        }

        return normalized;
    }

}