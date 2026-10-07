package io.github.imecuadorian.emitta.documentartifact.application.port.out;

import io.github.imecuadorian.emitta.documentartifact.application.model.StoredArtifactObject;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.UUID;

public interface DocumentArtifactStoragePort {

    StoredArtifactObject store(
            UUID documentId,
            DocumentArtifactType type,
            String contentType,
            String sha256,
            byte[] content
    );
}