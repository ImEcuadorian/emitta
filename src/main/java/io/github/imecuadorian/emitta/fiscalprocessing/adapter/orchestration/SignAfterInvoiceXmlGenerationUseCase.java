package io.github.imecuadorian.emitta.fiscalprocessing.adapter.orchestration;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignAndFinalizeFiscalDocumentUseCase;

import java.util.Objects;

public final class SignAfterInvoiceXmlGenerationUseCase
        implements ProcessFiscalDocumentUseCase {

    private final ProcessFiscalDocumentUseCase delegate;

    private final SignAndFinalizeFiscalDocumentUseCase
            signAndFinalizeFiscalDocumentUseCase;

    public SignAfterInvoiceXmlGenerationUseCase(
            ProcessFiscalDocumentUseCase delegate,
            SignAndFinalizeFiscalDocumentUseCase
                    signAndFinalizeFiscalDocumentUseCase
    ) {

        this.delegate =
                Objects.requireNonNull(
                        delegate
                );

        this.signAndFinalizeFiscalDocumentUseCase =
                Objects.requireNonNull(
                        signAndFinalizeFiscalDocumentUseCase
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
                        != DocumentStatus.GENERATING
        ) {

            return result;
        }

        Document signedDocument =
                signAndFinalizeFiscalDocumentUseCase
                        .signAndFinalize(
                                result.documentId()
                        );

        if (
                signedDocument.getStatus()
                        != DocumentStatus.SIGNED
        ) {

            throw new IllegalStateException(
                    "Signing pipeline completed without SIGNED status: "
                            + signedDocument.getStatus()
            );
        }

        return new ProcessFiscalDocumentResult(
                result.documentId(),
                signedDocument.getStatus(),
                result.processed()
        );
    }
}