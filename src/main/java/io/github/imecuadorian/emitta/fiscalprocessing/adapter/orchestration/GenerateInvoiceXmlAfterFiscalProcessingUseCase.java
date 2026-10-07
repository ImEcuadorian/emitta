package io.github.imecuadorian.emitta.fiscalprocessing.adapter.orchestration;

import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateAndStoreInvoiceXmlUseCase;

import java.util.Objects;

public final class GenerateInvoiceXmlAfterFiscalProcessingUseCase
        implements ProcessFiscalDocumentUseCase {

    private final ProcessFiscalDocumentUseCase delegate;

    private final GenerateAndStoreInvoiceXmlUseCase
            generateAndStoreInvoiceXmlUseCase;

    public GenerateInvoiceXmlAfterFiscalProcessingUseCase(
            ProcessFiscalDocumentUseCase delegate,
            GenerateAndStoreInvoiceXmlUseCase generateAndStoreInvoiceXmlUseCase
    ) {

        this.delegate =
                Objects.requireNonNull(
                        delegate
                );

        this.generateAndStoreInvoiceXmlUseCase =
                Objects.requireNonNull(
                        generateAndStoreInvoiceXmlUseCase
                );
    }

    @Override
    public ProcessFiscalDocumentResult process(
            ProcessFiscalDocumentCommand command
    ) {

        ProcessFiscalDocumentResult result =
                delegate.process(
                        command
                );

        if (
                result.status()
                        == DocumentStatus.GENERATING
        ) {

            generateAndStoreInvoiceXmlUseCase
                    .generateAndStore(
                            result.documentId()
                    );
        }

        return result;
    }
}