package io.github.imecuadorian.emitta.fiscalsigning.application.port.in;

import io.github.imecuadorian.emitta.document.domain.Document;

import java.util.UUID;

public interface MarkDocumentSignedUseCase {

    Document markSigned(
            UUID documentId
    );
}