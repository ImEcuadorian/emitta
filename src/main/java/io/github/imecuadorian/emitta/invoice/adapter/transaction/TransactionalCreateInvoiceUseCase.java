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
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public TransactionalCreateInvoiceUseCase(
            CreateInvoiceUseCase delegate,
            TransactionTemplate transactionTemplate
    ) {
        this(delegate,transactionTemplate,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }
    public TransactionalCreateInvoiceUseCase(CreateInvoiceUseCase delegate,TransactionTemplate transactionTemplate,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics=metrics;
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
                metrics.timer("emitta.invoice.transaction.duration").record(() -> transactionTemplate.execute(
                        status -> {
                            if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                                new org.springframework.transaction.support.TransactionSynchronization() {
                                    private long started;
                                    @Override public void beforeCommit(boolean readOnly) { started=System.nanoTime(); }
                                    @Override public void afterCommit() {
                                        if(started!=0)metrics.timer("emitta.invoice.commit.duration").record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
                                    }
                                });
                            return delegate.create(command);
                        }
                ));
        if(result!=null && result.created()) {
            Runnable committed=()->{
                metrics.counter("emitta.invoice.committed").increment();
                metrics.counter("emitta.outbox.generated").increment();
            };
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){committed.run();}});
            else committed.run();
        }

        return Objects.requireNonNull(
                result,
                "Transactional invoice creation returned null"
        );
    }
}
