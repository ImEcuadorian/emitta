package io.github.imecuadorian.emitta.invoice.adapter.in.web;

public final class InvalidTenantContextException
        extends RuntimeException {

    public InvalidTenantContextException(
            String message
    ) {
        super(
                message
        );
    }
}