package io.github.imecuadorian.emitta.srireception.adapter.transaction;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentRejectedUseCase;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

public final class TransactionalMarkDocumentRejectedUseCase
        implements MarkDocumentRejectedUseCase {

    private final MarkDocumentRejectedUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalMarkDocumentRejectedUseCase(
            MarkDocumentRejectedUseCase delegate,
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
    public Document markRejected(
            UUID documentId
    ) {

        Document result =
                transactionTemplate.execute(
                        status ->
                                delegate.markRejected(
                                        documentId
                                )
                );

        return Objects.requireNonNull(
                result,
                "Mark document rejected returned null"
        );
    }
}