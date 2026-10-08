package io.github.imecuadorian.emitta.sriauthorization.application.port.in;

import io.github.imecuadorian.emitta.document.domain.Document;

import java.util.UUID;

public interface MarkDocumentAuthorizedUseCase {

    Document markAuthorized(
            UUID documentId
    );
}