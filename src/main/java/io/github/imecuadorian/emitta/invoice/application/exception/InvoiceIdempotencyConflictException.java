package io.github.imecuadorian.emitta.invoice.application.exception;

import java.util.UUID;

public final class InvoiceIdempotencyConflictException
        extends RuntimeException {

    public InvoiceIdempotencyConflictException(
            UUID documentId
    ) {
        super(
                "Idempotency key already belongs to a different invoice payload. Document: "
                        + documentId
        );
    }
}