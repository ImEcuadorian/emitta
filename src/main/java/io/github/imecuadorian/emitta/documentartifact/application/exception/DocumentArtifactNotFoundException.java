package io.github.imecuadorian.emitta.documentartifact.application.exception;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.UUID;

public final class DocumentArtifactNotFoundException
        extends RuntimeException {

    public DocumentArtifactNotFoundException(
            UUID documentId,
            DocumentArtifactType type
    ) {

        super(
                "Artifact not found: "
                        + documentId
                        + " / "
                        + type
        );
    }
}