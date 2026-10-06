package io.github.imecuadorian.emitta.invoice.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
public final class Invoice {

    private final UUID documentId;
    private final UUID customerId;

    private final BuyerSnapshot buyer;

    private final BigDecimal subtotal;
    private final BigDecimal discountTotal;
    private final BigDecimal taxTotal;
    private final BigDecimal total;

    private final String currency;

    private final List<InvoiceItem> items;
    private final List<InvoicePayment> payments;

    private Invoice(
            UUID documentId,
            UUID customerId,
            BuyerSnapshot buyer,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal total,
            String currency,
            List<InvoiceItem> items,
            List<InvoicePayment> payments
    ) {
        this.documentId = documentId;
        this.customerId = customerId;
        this.buyer = buyer;
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.total = total;
        this.currency = currency;
        this.items = List.copyOf(items);
        this.payments = List.copyOf(payments);
    }

    public static Invoice restore(
            UUID documentId,
            UUID customerId,
            BuyerSnapshot buyer,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal total,
            String currency,
            List<InvoiceItem> items,
            List<InvoicePayment> payments
    ) {

        return new Invoice(
                documentId,
                customerId,
                buyer,
                subtotal,
                discountTotal,
                taxTotal,
                total,
                currency,
                items,
                payments
        );
    }

    public static Invoice create(
            UUID documentId,
            UUID customerId,
            BuyerSnapshot buyer,
            List<InvoiceItem> items,
            List<InvoicePayment> payments,
            String currency
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                buyer,
                "Buyer cannot be null"
        );

        if (
                items == null
                        || items.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Invoice must contain at least one item"
            );
        }

        ensureUniqueItemLines(
                items
        );

        BigDecimal subtotal =
                sum(
                        items.stream()
                                .map(
                                        InvoiceItem::getSubtotal
                                )
                                .toList()
                );

        BigDecimal discountTotal =
                sum(
                        items.stream()
                                .map(
                                        InvoiceItem::getDiscount
                                )
                                .toList()
                );

        BigDecimal taxTotal =
                sum(
                        items.stream()
                                .map(
                                        InvoiceItem::getTaxTotal
                                )
                                .toList()
                );

        BigDecimal total =
                DecimalRules.money(
                        subtotal.add(
                                taxTotal
                        ),
                        "Invoice total"
                );

        if (
                payments == null
                        || payments.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Invoice must contain at least one payment"
            );
        }

        ensureUniquePaymentLines(
                payments
        );

        BigDecimal paymentTotal =
                sum(
                        payments.stream()
                                .map(
                                        InvoicePayment::total
                                )
                                .toList()
                );

        if (
                paymentTotal.compareTo(
                        total
                ) != 0
        ) {
            throw new IllegalArgumentException(
                    "Payment total does not match invoice total. "
                            + "Expected "
                            + total
                            + " but received "
                            + paymentTotal
            );
        }

        String normalizedCurrency =
                Objects.requireNonNull(
                                currency,
                                "Currency cannot be null"
                        )
                        .trim();

        if (
                normalizedCurrency.isEmpty()
                        || normalizedCurrency.length() > 15
        ) {
            throw new IllegalArgumentException(
                    "Currency must contain between 1 and 15 characters"
            );
        }

        return new Invoice(
                documentId,
                customerId,
                buyer,
                subtotal,
                discountTotal,
                taxTotal,
                total,
                normalizedCurrency,
                items,
                payments
        );
    }

    private static BigDecimal sum(
            List<BigDecimal> values
    ) {

        return DecimalRules.money(
                values.stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        ),
                "Aggregate amount"
        );
    }

    private static void ensureUniqueItemLines(
            List<InvoiceItem> items
    ) {

        Set<Integer> lineNumbers =
                new HashSet<>();

        for (InvoiceItem item : items) {

            Objects.requireNonNull(
                    item,
                    "Invoice item cannot be null"
            );

            if (
                    !lineNumbers.add(
                            item.getLineNumber()
                    )
            ) {
                throw new IllegalArgumentException(
                        "Duplicate invoice item line number: "
                                + item.getLineNumber()
                );
            }
        }
    }

    private static void ensureUniquePaymentLines(
            List<InvoicePayment> payments
    ) {

        Set<Integer> lineNumbers =
                new HashSet<>();

        for (
                InvoicePayment payment
                : payments
        ) {

            Objects.requireNonNull(
                    payment,
                    "Invoice payment cannot be null"
            );

            if (
                    !lineNumbers.add(
                            payment.lineNumber()
                    )
            ) {
                throw new IllegalArgumentException(
                        "Duplicate payment line number: "
                                + payment.lineNumber()
                );
            }
        }
    }

}