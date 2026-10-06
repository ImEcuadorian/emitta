package io.github.imecuadorian.emitta.document.application.exception;

import java.util.UUID;

public final class DocumentFiscalResourceNotFoundException
        extends RuntimeException {

    private final String resource;
    private final UUID resourceId;

    public DocumentFiscalResourceNotFoundException(
            String resource,
            UUID resourceId
    ) {
        super(
                resource
                        + " not found: "
                        + resourceId
        );

        this.resource = resource;
        this.resourceId = resourceId;
    }

    public String getResource() {
        return resource;
    }

    public UUID getResourceId() {
        return resourceId;
    }
}