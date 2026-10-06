package io.github.imecuadorian.emitta.document.adapter.config;

import io.github.imecuadorian.emitta.document.adapter.transaction.TransactionalCreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.application.service.CreateDocumentService;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxEventPort;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class DocumentConfiguration {

    @Bean
    CreateDocumentUseCase createDocumentUseCase(
            DocumentRepository documentRepository,
            PointOfIssueFiscalLookupUseCase pointOfIssueLookup,
            EstablishmentFiscalLookupUseCase establishmentLookup,
            TaxpayerFiscalLookupUseCase taxpayerLookup,
            OutboxEventPort outboxEventPort,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {

        CreateDocumentService service =
                new CreateDocumentService(
                        documentRepository,
                        pointOfIssueLookup,
                        establishmentLookup,
                        taxpayerLookup,
                        outboxEventPort,
                        clock,
                        UUID::randomUUID
                );

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return new TransactionalCreateDocumentUseCase(
                service,
                transactionTemplate
        );
    }
}