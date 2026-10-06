package io.github.imecuadorian.emitta.fiscalprocessing.application.port.in;

import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;

public interface ProcessFiscalDocumentUseCase {

    ProcessFiscalDocumentResult process(
            ProcessFiscalDocumentCommand command
    );
}