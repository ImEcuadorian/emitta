package io.github.imecuadorian.emitta.invoice.adapter.transaction;

import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalCreateInvoiceUseCase
        implements CreateInvoiceUseCase {

    private final CreateInvoiceUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalCreateInvoiceUseCase(
            CreateInvoiceUseCase delegate,
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
    public CreateInvoiceResult create(
            CreateInvoiceCommand command
    ) {

        CreateInvoiceResult result =
                transactionTemplate.execute(
                        status ->
                                delegate.create(
                                        command
                                )
                );

        return Objects.requireNonNull(
                result,
                "Transactional invoice creation returned null"
        );
    }
}