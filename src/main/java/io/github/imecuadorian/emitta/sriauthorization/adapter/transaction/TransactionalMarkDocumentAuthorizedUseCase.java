package io.github.imecuadorian.emitta.sriauthorization.adapter.transaction;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.MarkDocumentAuthorizedUseCase;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

public final class TransactionalMarkDocumentAuthorizedUseCase
        implements MarkDocumentAuthorizedUseCase {

    private final MarkDocumentAuthorizedUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalMarkDocumentAuthorizedUseCase(
            MarkDocumentAuthorizedUseCase delegate,
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
    public Document markAuthorized(
            UUID documentId
    ) {

        Document result =
                transactionTemplate.execute(
                        status ->
                                delegate.markAuthorized(
                                        documentId
                                )
                );

        return Objects.requireNonNull(
                result,
                "Mark document authorized returned null"
        );
    }
}