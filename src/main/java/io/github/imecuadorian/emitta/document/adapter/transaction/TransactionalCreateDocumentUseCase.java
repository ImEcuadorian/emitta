package io.github.imecuadorian.emitta.document.adapter.transaction;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalCreateDocumentUseCase
        implements CreateDocumentUseCase {

    private final CreateDocumentUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalCreateDocumentUseCase(
            CreateDocumentUseCase delegate,
            TransactionTemplate transactionTemplate
    ) {
        this.delegate =
                Objects.requireNonNull(
                        delegate,
                        "Delegate cannot be null"
                );

        this.transactionTemplate =
                Objects.requireNonNull(
                        transactionTemplate,
                        "Transaction template cannot be null"
                );
    }

    @Override
    public CreateDocumentResult create(
            CreateDocumentCommand command
    ) {

        CreateDocumentResult result =
                transactionTemplate.execute(
                        status ->
                                delegate.create(
                                        command
                                )
                );

        return Objects.requireNonNull(
                result,
                "Transactional document creation returned null"
        );
    }
}