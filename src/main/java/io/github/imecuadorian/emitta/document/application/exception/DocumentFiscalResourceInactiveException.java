package io.github.imecuadorian.emitta.document.application.exception;

import java.util.UUID;

public final class DocumentFiscalResourceInactiveException
        extends RuntimeException {

    public DocumentFiscalResourceInactiveException(
            String resource,
            UUID resourceId
    ) {
        super(
                resource
                        + " is inactive: "
                        + resourceId
        );
    }
}