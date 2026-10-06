package io.github.imecuadorian.emitta.invoice.application.model;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.invoice.domain.Invoice;

import java.util.Objects;

public record CreateInvoiceResult(
        Document document,
        Invoice invoice,
        boolean created
) {

    public CreateInvoiceResult {

        Objects.requireNonNull(
                document,
                "Document cannot be null"
        );

        Objects.requireNonNull(
                invoice,
                "Invoice cannot be null"
        );
    }
}