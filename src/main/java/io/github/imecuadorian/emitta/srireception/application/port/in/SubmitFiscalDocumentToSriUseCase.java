package io.github.imecuadorian.emitta.srireception.application.port.in;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;

import java.util.UUID;

public interface SubmitFiscalDocumentToSriUseCase {

    SriReceptionResult submit(
            UUID documentId
    );
}