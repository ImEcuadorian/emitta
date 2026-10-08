package io.github.imecuadorian.emitta.sriauthorization.application.port.in;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;

import java.util.UUID;

public interface StoreAuthorizedDocumentXmlUseCase {

    DocumentArtifact store(
            UUID documentId,
            SriAuthorizationResult authorization
    );
}