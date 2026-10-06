package io.github.imecuadorian.emitta.invoice.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.invoice.adapter.transaction.TransactionalCreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.application.port.out.InvoiceRepository;
import io.github.imecuadorian.emitta.invoice.application.service.CreateInvoiceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Configuration
public class InvoiceConfiguration {

    @Bean
    CreateInvoiceUseCase createInvoiceUseCase(
            CreateDocumentUseCase createDocumentUseCase,
            InvoiceRepository invoiceRepository,
            PlatformTransactionManager transactionManager
    ) {

        CreateInvoiceService service =
                new CreateInvoiceService(
                        createDocumentUseCase,
                        invoiceRepository,
                        UUID::randomUUID
                );

        return new TransactionalCreateInvoiceUseCase(
                service,
                new TransactionTemplate(
                        transactionManager
                )
        );
    }
}