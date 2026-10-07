package io.github.imecuadorian.emitta.fiscalprocessing.adapter.config;

import io.github.imecuadorian.emitta.accesskey.application.port.in.GenerateAccessKeyUseCase;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.fiscalprocessing.adapter.orchestration.GenerateInvoiceXmlAfterFiscalProcessingUseCase;
import io.github.imecuadorian.emitta.fiscalprocessing.adapter.transaction.TransactionalProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalprocessing.application.service.ProcessFiscalDocumentService;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateAndStoreInvoiceXmlUseCase;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class FiscalProcessingConfiguration {

    @Bean
    ProcessFiscalDocumentUseCase processFiscalDocumentUseCase(
            DocumentRepository documentRepository,
            GenerateAccessKeyUseCase generateAccessKeyUseCase,
            Clock clock,
            PlatformTransactionManager transactionManager,
            ObjectProvider<GenerateAndStoreInvoiceXmlUseCase>
                    generateAndStoreInvoiceXmlUseCaseProvider,
            @Value(
                    "${emitta.fiscal.issue-zone:America/Guayaquil}"
            )
            String issueZone
    ) {

        ProcessFiscalDocumentService service =
                new ProcessFiscalDocumentService(
                        documentRepository,
                        generateAccessKeyUseCase,
                        clock,
                        ZoneId.of(
                                issueZone
                        )
                );

        ProcessFiscalDocumentUseCase transactional =
                new TransactionalProcessFiscalDocumentUseCase(
                        service,
                        new TransactionTemplate(
                                transactionManager
                        )
                );

        GenerateAndStoreInvoiceXmlUseCase
                generateAndStoreInvoiceXmlUseCase =
                generateAndStoreInvoiceXmlUseCaseProvider
                        .getIfAvailable();

        if (generateAndStoreInvoiceXmlUseCase == null) {

            return transactional;
        }

        return new GenerateInvoiceXmlAfterFiscalProcessingUseCase(
                transactional,
                generateAndStoreInvoiceXmlUseCase
        );
    }
}