package io.github.imecuadorian.emitta.invoice.application.command;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateInvoiceCommand(
        UUID tenantId,
        UUID pointOfIssueId,
        FiscalEnvironment environment,
        String idempotencyKey,
        Instant issuedAt,

        UUID customerId,
        InvoiceBuyerCommand buyer,

        List<InvoiceItemCommand> items,
        List<InvoicePaymentCommand> payments,

        BigDecimal expectedTotal
) {

    public CreateInvoiceCommand {

        Objects.requireNonNull(
                tenantId,
                "Tenant id cannot be null"
        );

        Objects.requireNonNull(
                pointOfIssueId,
                "Point of issue id cannot be null"
        );

        Objects.requireNonNull(
                environment,
                "Environment cannot be null"
        );

        Objects.requireNonNull(
                idempotencyKey,
                "Idempotency key cannot be null"
        );

        Objects.requireNonNull(
                issuedAt,
                "Issued at cannot be null"
        );

        Objects.requireNonNull(
                buyer,
                "Buyer cannot be null"
        );

        items =
                items == null
                        ? List.of()
                        : List.copyOf(items);

        payments =
                payments == null
                        ? List.of()
                        : List.copyOf(payments);
    }
}