package io.github.imecuadorian.emitta.document.application.port.in;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;

public interface CreateDocumentUseCase {

    CreateDocumentResult create(
            CreateDocumentCommand command
    );
}