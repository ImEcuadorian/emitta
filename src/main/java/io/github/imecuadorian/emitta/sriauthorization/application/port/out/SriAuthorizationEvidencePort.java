package io.github.imecuadorian.emitta.sriauthorization.application.port.out;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;

import java.util.Optional;
import java.util.UUID;

public interface SriAuthorizationEvidencePort {

    SriAuthorizationEvidence save(
            SriAuthorizationEvidence evidence
    );

    Optional<SriAuthorizationEvidence> findByDocumentId(
            UUID documentId
    );
}