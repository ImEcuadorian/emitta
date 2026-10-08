package io.github.imecuadorian.emitta.srireception.adapter.transaction;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.srireception.application.port.in.ScheduleDocumentRetryUseCase;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

public final class TransactionalScheduleDocumentRetryUseCase
        implements ScheduleDocumentRetryUseCase {

    private final ScheduleDocumentRetryUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalScheduleDocumentRetryUseCase(
            ScheduleDocumentRetryUseCase delegate,
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
    public Document scheduleRetry(
            UUID documentId
    ) {

        Document result =
                transactionTemplate.execute(
                        status ->
                                delegate.scheduleRetry(
                                        documentId
                                )
                );

        return Objects.requireNonNull(
                result,
                "Schedule document retry returned null"
        );
    }
}