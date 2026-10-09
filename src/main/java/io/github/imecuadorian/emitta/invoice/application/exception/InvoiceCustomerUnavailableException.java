package io.github.imecuadorian.emitta.invoice.application.exception;

/** Does not disclose whether a customer exists in another tenant. */
public final class InvoiceCustomerUnavailableException extends RuntimeException {
    public InvoiceCustomerUnavailableException() {
        super("Customer was not found in the authenticated tenant");
    }
}
