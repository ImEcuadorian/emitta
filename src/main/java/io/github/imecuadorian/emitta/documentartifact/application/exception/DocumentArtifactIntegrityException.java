package io.github.imecuadorian.emitta.documentartifact.application.exception;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.UUID;

public final class DocumentArtifactIntegrityException
        extends RuntimeException {

    public DocumentArtifactIntegrityException(
            UUID documentId,
            DocumentArtifactType type
    ) {

        this(
                documentId,
                type,
                "Artifact content integrity violation"
        );
    }

    public DocumentArtifactIntegrityException(
            UUID documentId,
            DocumentArtifactType type,
            String reason
    ) {

        super(
                reason
                        + ": "
                        + documentId
                        + " / "
                        + type
        );
    }
}