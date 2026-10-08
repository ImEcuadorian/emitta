package io.github.imecuadorian.emitta.documentartifact.application.port.in;

import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.UUID;

public interface LoadDocumentArtifactUseCase {

    LoadedDocumentArtifact load(
            UUID documentId,
            DocumentArtifactType type
    );
}