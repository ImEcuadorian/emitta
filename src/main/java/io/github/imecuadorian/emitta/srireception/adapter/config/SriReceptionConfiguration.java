package io.github.imecuadorian.emitta.srireception.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;

import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;

import io.github.imecuadorian.emitta.srireception.adapter.out.persistence.PostgreSqlSriReceptionAttemptAdapter;
import io.github.imecuadorian.emitta.srireception.adapter.out.soap.SriSoapReceptionAdapter;

import io.github.imecuadorian.emitta.srireception.adapter.transaction.TransactionalMarkDocumentRejectedUseCase;
import io.github.imecuadorian.emitta.srireception.adapter.transaction.TransactionalMarkDocumentSubmittedUseCase;
import io.github.imecuadorian.emitta.srireception.adapter.transaction.TransactionalScheduleDocumentRetryUseCase;

import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentRejectedUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentSubmittedUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.ScheduleDocumentRetryUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitFiscalDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitSignedDocumentToSriUseCase;

import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionAttemptPort;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionPort;

import io.github.imecuadorian.emitta.srireception.application.service.MarkDocumentRejectedService;
import io.github.imecuadorian.emitta.srireception.application.service.MarkDocumentSubmittedService;
import io.github.imecuadorian.emitta.srireception.application.service.ScheduleDocumentRetryService;
import io.github.imecuadorian.emitta.srireception.application.service.SubmitFiscalDocumentToSriService;
import io.github.imecuadorian.emitta.srireception.application.service.SubmitSignedDocumentToSriService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.time.Clock;
import io.github.imecuadorian.emitta.shared.fiscal.ProviderPolicyPort;
import io.github.imecuadorian.emitta.srireception.application.service.ProviderSubmissionGuard;

@Configuration
@ConditionalOnProperty(
        prefix = "emitta.sri.reception",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(
        SriReceptionProperties.class
)
public class SriReceptionConfiguration {

    @Bean
    ProviderSubmissionGuard providerSubmissionGuard(ProviderPolicyPort policy,
                                                   LoadDocumentArtifactUseCase artifacts) {
        return new ProviderSubmissionGuard(policy, artifacts);
    }

    @Bean
    HttpClient sriReceptionHttpClient(
            SriReceptionProperties properties
    ) {

        return HttpClient.newBuilder()
                .connectTimeout(
                        properties.connectTimeout()
                )
                .build();
    }

    @Bean
    SriReceptionPort sriReceptionPort(
            @Qualifier("sriReceptionHttpClient")
            HttpClient sriReceptionHttpClient,
            SriReceptionProperties properties
    ) {

        return new SriSoapReceptionAdapter(
                sriReceptionHttpClient,
                properties.testEndpoint(),
                properties.productionEndpoint(),
                properties.requestTimeout()
        );
    }

    @Bean
    MarkDocumentSubmittedUseCase
    markDocumentSubmittedUseCase(
            DocumentRepository documentRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {

        MarkDocumentSubmittedUseCase delegate =
                new MarkDocumentSubmittedService(
                        documentRepository,
                        clock
                );

        return new TransactionalMarkDocumentSubmittedUseCase(
                delegate,
                new TransactionTemplate(
                        transactionManager
                )
        );
    }

    @Bean
    MarkDocumentRejectedUseCase
    markDocumentRejectedUseCase(
            DocumentRepository documentRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {

        MarkDocumentRejectedUseCase delegate =
                new MarkDocumentRejectedService(
                        documentRepository,
                        clock
                );

        return new TransactionalMarkDocumentRejectedUseCase(
                delegate,
                new TransactionTemplate(
                        transactionManager
                )
        );
    }

    @Bean
    ScheduleDocumentRetryUseCase
    scheduleDocumentRetryUseCase(
            DocumentRepository documentRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {

        ScheduleDocumentRetryUseCase delegate =
                new ScheduleDocumentRetryService(
                        documentRepository,
                        clock
                );

        return new TransactionalScheduleDocumentRetryUseCase(
                delegate,
                new TransactionTemplate(
                        transactionManager
                )
        );
    }

    @Bean
    SubmitSignedDocumentToSriUseCase
    submitSignedDocumentToSriUseCase(
            DocumentRepository documentRepository,
            LoadDocumentArtifactUseCase loadDocumentArtifactUseCase,
            SriReceptionPort sriReceptionPort,
            ProviderSubmissionGuard providerGuard
    ) {

        var delegate = new SubmitSignedDocumentToSriService(
                documentRepository,
                loadDocumentArtifactUseCase,
                sriReceptionPort
        );
        return documentId -> {
            providerGuard.verify(documentId);
            return delegate.submit(documentId);
        };
    }

    @Bean
    SriReceptionAttemptPort sriReceptionAttemptPort(
            JdbcClient jdbcClient,
            PlatformTransactionManager transactionManager
    ) {

        return new PostgreSqlSriReceptionAttemptAdapter(
                jdbcClient,
                new TransactionTemplate(
                        transactionManager
                )
        );
    }

    @Bean
    SubmitFiscalDocumentToSriUseCase submitFiscalDocumentToSriUseCase(
            MarkDocumentSubmittedUseCase markDocumentSubmittedUseCase,
            SubmitSignedDocumentToSriUseCase submitSignedDocumentToSriUseCase,
            MarkDocumentRejectedUseCase markDocumentRejectedUseCase,
            ScheduleDocumentRetryUseCase scheduleDocumentRetryUseCase,
            SriReceptionAttemptPort sriReceptionAttemptPort,
            Clock clock,
            ProviderSubmissionGuard providerGuard
    ) {

        var delegate = new SubmitFiscalDocumentToSriService(
                markDocumentSubmittedUseCase,
                submitSignedDocumentToSriUseCase,
                markDocumentRejectedUseCase,
                scheduleDocumentRetryUseCase,
                sriReceptionAttemptPort,
                clock
        );
        return documentId -> {
            providerGuard.verify(documentId);
            return delegate.submit(documentId);
        };
    }
}
