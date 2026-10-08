package io.github.imecuadorian.emitta.srireception.adapter.transaction;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentSubmittedUseCase;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

public final class TransactionalMarkDocumentSubmittedUseCase
        implements MarkDocumentSubmittedUseCase {

    private final MarkDocumentSubmittedUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalMarkDocumentSubmittedUseCase(
            MarkDocumentSubmittedUseCase delegate,
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
    public Document markSubmitted(
            UUID documentId
    ) {

        Document result =
                transactionTemplate.execute(
                        status ->
                                delegate.markSubmitted(
                                        documentId
                                )
                );

        return Objects.requireNonNull(
                result,
                "Mark document submitted returned null"
        );
    }
}