package io.github.imecuadorian.emitta.document.application.exception;

public final class DocumentIdempotencyConflictException
        extends RuntimeException {

    private final String idempotencyKey;

    public DocumentIdempotencyConflictException(
            String idempotencyKey
    ) {
        super(
                "Idempotency key was already used for a different request: "
                        + idempotencyKey
        );

        this.idempotencyKey =
                idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}