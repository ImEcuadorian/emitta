package io.github.imecuadorian.emitta.srireception.application.port.in;

import io.github.imecuadorian.emitta.document.domain.Document;

import java.util.UUID;

public interface MarkDocumentSubmittedUseCase {

    Document markSubmitted(
            UUID documentId
    );
}