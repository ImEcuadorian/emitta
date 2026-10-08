package io.github.imecuadorian.emitta.fiscalsigning.application.port.in;

import io.github.imecuadorian.emitta.document.domain.Document;

import java.util.UUID;

public interface SignAndFinalizeFiscalDocumentUseCase {

    Document signAndFinalize(
            UUID documentId
    );
}