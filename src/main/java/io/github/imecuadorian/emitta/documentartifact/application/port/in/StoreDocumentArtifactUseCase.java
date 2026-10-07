package io.github.imecuadorian.emitta.documentartifact.application.port.in;

import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;

public interface StoreDocumentArtifactUseCase {

    DocumentArtifact store(
            StoreDocumentArtifactCommand command
    );
}