package io.github.imecuadorian.emitta.fiscalsigning.application.port.in;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;

import java.util.UUID;

public interface SignFiscalDocumentUseCase {

    DocumentArtifact sign(
            UUID documentId
    );
}