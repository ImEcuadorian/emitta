package io.github.imecuadorian.emitta.fiscalprocessing.adapter.transaction;

import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalProcessFiscalDocumentUseCase
        implements ProcessFiscalDocumentUseCase {

    private final ProcessFiscalDocumentUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalProcessFiscalDocumentUseCase(
            ProcessFiscalDocumentUseCase delegate,
            TransactionTemplate transactionTemplate
    ) {
        this.delegate =
                Objects.requireNonNull(
                        delegate
                );

        this.transactionTemplate =
                Objects.requireNonNull(
                        transactionTemplate
                );
    }

    @Override
    public ProcessFiscalDocumentResult process(
            ProcessFiscalDocumentCommand command
    ) {

        ProcessFiscalDocumentResult result =
                transactionTemplate.execute(
                        status ->
                                delegate.process(
                                        command
                                )
                );

        return Objects.requireNonNull(
                result,
                "Fiscal document processing returned null"
        );
    }
}