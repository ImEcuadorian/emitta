
package io.github.imecuadorian.emitta.sriauthorization.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml.JdkSriXadesSignatureVerifier;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;

import io.github.imecuadorian.emitta.sriauthorization.adapter.out.persistence.PostgreSqlSriAuthorizationEvidenceAdapter;
import io.github.imecuadorian.emitta.sriauthorization.adapter.transaction.TransactionalMarkDocumentAuthorizedUseCase;

import io.github.imecuadorian.emitta.sriauthorization.application.port.in.MarkDocumentAuthorizedUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.QueryDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.StoreAuthorizedDocumentXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.VerifyAuthorizedXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;

import io.github.imecuadorian.emitta.sriauthorization.application.service.MarkDocumentAuthorizedService;
import io.github.imecuadorian.emitta.sriauthorization.application.service.ReconcileDocumentAuthorizationService;
import io.github.imecuadorian.emitta.sriauthorization.application.service.StoreAuthorizedDocumentXmlService;
import io.github.imecuadorian.emitta.sriauthorization.application.service.VerifyAuthorizedXmlService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "emitta.sri.authorization",
        name = "enabled",
        havingValue = "true"
)
public class SriAuthorizationWorkflowConfiguration {

    @Bean
    SriAuthorizationEvidencePort sriAuthorizationEvidencePort(
            JdbcClient jdbcClient,
            DataSource dataSource
    ) {
        return new PostgreSqlSriAuthorizationEvidenceAdapter(
                jdbcClient,
                new TransactionTemplate(
                        new DataSourceTransactionManager(dataSource)
                )
        );
    }

    @Bean
    XmlSignatureVerifierPort xmlSignatureVerifierPort() {
        return new JdkSriXadesSignatureVerifier();
    }

    @Bean
    VerifyAuthorizedXmlUseCase verifyAuthorizedXmlUseCase() {
        return new VerifyAuthorizedXmlService();
    }

    @Bean
    StoreAuthorizedDocumentXmlUseCase storeAuthorizedDocumentXmlUseCase(
            DocumentRepository documentRepository,
            StoreDocumentArtifactUseCase storeDocumentArtifactUseCase
    ) {
        return new StoreAuthorizedDocumentXmlService(
                documentRepository,
                storeDocumentArtifactUseCase
        );
    }

    @Bean
    MarkDocumentAuthorizedUseCase markDocumentAuthorizedUseCase(
            DocumentRepository documentRepository,
            SriAuthorizationEvidencePort evidencePort,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        MarkDocumentAuthorizedService service =
                new MarkDocumentAuthorizedService(
                        documentRepository,
                        evidencePort,
                        clock
                );

        return new TransactionalMarkDocumentAuthorizedUseCase(
                service,
                new TransactionTemplate(transactionManager)
        );
    }

    @Bean
    ReconcileDocumentAuthorizationUseCase reconcileDocumentAuthorizationUseCase(
            QueryDocumentAuthorizationUseCase queryUseCase,
            LoadDocumentArtifactUseCase loadArtifactUseCase,
            VerifyAuthorizedXmlUseCase verifyXmlUseCase,
            StoreAuthorizedDocumentXmlUseCase storeXmlUseCase,
            XmlSignatureVerifierPort signatureVerifier,
            MarkDocumentAuthorizedUseCase markAuthorizedUseCase,
            DocumentRepository documentRepository,
            SriAuthorizationEvidencePort evidencePort,
            org.springframework.beans.factory.ObjectProvider<io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase> rideProvider,
            io.micrometer.core.instrument.MeterRegistry metrics,
            io.github.imecuadorian.emitta.recovery.DurableWorkPort jobs
    ) {
        var reconciliation = new ReconcileDocumentAuthorizationService(
                queryUseCase,
                loadArtifactUseCase,
                verifyXmlUseCase,
                storeXmlUseCase,
                signatureVerifier,
                markAuthorizedUseCase,
                documentRepository,
                evidencePort
        );
        return documentId -> {
            var result = reconciliation.reconcile(documentId);
            if (result.status() == io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus.AUTHORIZED) {
                var ride = rideProvider.getIfAvailable();
                if (ride != null) {
                    try {
                        var document = documentRepository.findById(documentId).orElseThrow();
                        jobs.enqueue(io.github.imecuadorian.emitta.recovery.DurableWorkPort.Kind.RIDE,document.getTenantId(),documentId);
                        metrics.counter("emitta.ride.enqueue", "result", "success").increment();
                    } catch (RuntimeException failure) {
                        // Authorization is already durable. PDF failure must never roll it back or resubmit.
                        metrics.counter("emitta.ride.enqueue", "result", "failure").increment();
                        org.slf4j.LoggerFactory.getLogger(SriAuthorizationWorkflowConfiguration.class)
                                .error("RIDE durable enqueue failed for document {}; failureType={}", documentId,
                                        failure.getClass().getSimpleName());
                    }
                }
            }
            return result;
        };
    }
}
