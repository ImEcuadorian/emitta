package io.github.imecuadorian.emitta.invoicexml.application.exception;

public final class InvalidInvoiceXmlException
        extends RuntimeException {

    public InvalidInvoiceXmlException(
            String message,
            Throwable cause
    ) {

        super(
                message,
                cause
        );
    }
}