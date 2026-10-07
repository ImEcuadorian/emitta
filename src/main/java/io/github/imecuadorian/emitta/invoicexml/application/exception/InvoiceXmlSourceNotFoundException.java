package io.github.imecuadorian.emitta.invoicexml.application.exception;

import java.util.UUID;

public final class InvoiceXmlSourceNotFoundException
        extends RuntimeException {

    public InvoiceXmlSourceNotFoundException(
            UUID documentId
    ) {

        super(
                "Invoice fiscal data not found for document: "
                        + documentId
        );
    }
}