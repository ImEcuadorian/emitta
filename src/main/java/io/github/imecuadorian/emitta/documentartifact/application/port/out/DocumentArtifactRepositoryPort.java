package io.github.imecuadorian.emitta.documentartifact.application.port.out;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.Optional;
import java.util.UUID;

public interface DocumentArtifactRepositoryPort {

    Optional<DocumentArtifact> findByDocumentIdAndType(
            UUID documentId,
            DocumentArtifactType type
    );

    DocumentArtifact save(
            DocumentArtifact artifact
    );
}