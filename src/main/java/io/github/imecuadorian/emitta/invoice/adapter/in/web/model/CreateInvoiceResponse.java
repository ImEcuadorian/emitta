package io.github.imecuadorian.emitta.invoice.adapter.in.web.model;

import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record CreateInvoiceResponse(
        UUID id,
        DocumentStatus status,
        BigDecimal total,
        boolean created
) {

    public CreateInvoiceResponse {

        Objects.requireNonNull(
                id,
                "Invoice id cannot be null"
        );

        Objects.requireNonNull(
                status,
                "Invoice status cannot be null"
        );

        Objects.requireNonNull(
                total,
                "Invoice total cannot be null"
        );
    }
}