package io.github.imecuadorian.emitta.fiscalsigning.adapter.transaction;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.MarkDocumentSignedUseCase;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

public final class TransactionalMarkDocumentSignedUseCase
        implements MarkDocumentSignedUseCase {

    private final MarkDocumentSignedUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalMarkDocumentSignedUseCase(
            MarkDocumentSignedUseCase delegate,
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
    public Document markSigned(
            UUID documentId
    ) {

        Document result =
                transactionTemplate.execute(
                        status ->
                                delegate.markSigned(
                                        documentId
                                )
                );

        return Objects.requireNonNull(
                result,
                "Mark document signed returned null"
        );
    }
}