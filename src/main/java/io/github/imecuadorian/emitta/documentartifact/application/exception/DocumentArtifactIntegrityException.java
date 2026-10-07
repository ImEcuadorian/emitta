package io.github.imecuadorian.emitta.documentartifact.application.exception;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.UUID;

public final class DocumentArtifactIntegrityException
        extends RuntimeException {

    public DocumentArtifactIntegrityException(
            UUID documentId,
            DocumentArtifactType type
    ) {

        super(
                "Artifact already exists with different content: "
                        + documentId
                        + " / "
                        + type
        );
    }
}