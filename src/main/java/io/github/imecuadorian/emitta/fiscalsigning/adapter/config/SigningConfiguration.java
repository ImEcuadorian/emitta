package io.github.imecuadorian.emitta.fiscalsigning.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactRepositoryPort;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import io.github.imecuadorian.emitta.documentartifact.application.service.LoadDocumentArtifactService;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.AesGcmSecretCipher;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.dss.DssXadesBesXmlSigner;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence.PostgreSqlSigningKeyMaterialAdapter;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.transaction.TransactionalMarkDocumentSignedUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.MarkDocumentSignedUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignAndFinalizeFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.SigningKeyMaterialPort;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignerPort;

import io.github.imecuadorian.emitta.fiscalsigning.application.service.MarkDocumentSignedService;
import io.github.imecuadorian.emitta.fiscalsigning.application.service.SignAndFinalizeFiscalDocumentService;
import io.github.imecuadorian.emitta.fiscalsigning.application.service.SignFiscalDocumentService;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
@ConditionalOnProperty(
        prefix = "emitta.signing",
        name = "enabled",
        havingValue = "true"
)
public class SigningConfiguration {



    @Bean
    AesGcmSecretCipher certificateSecretCipher(
            @Value("${EMITTA_CERTIFICATE_MASTER_KEY_B64}")
            String masterKeyBase64
    ) {

        return new AesGcmSecretCipher(
                masterKeyBase64
        );
    }

    @Bean
    SigningKeyMaterialPort signingKeyMaterialPort(
            JdbcClient jdbcClient,
            PlatformTransactionManager transactionManager,
            AesGcmSecretCipher certificateSecretCipher
    ) {

        return new PostgreSqlSigningKeyMaterialAdapter(
                jdbcClient,
                new TransactionTemplate(
                        transactionManager
                ),
                certificateSecretCipher
        );
    }

    @Bean
    XmlSignerPort xmlSignerPort(
            Clock clock
    ) {

        return new DssXadesBesXmlSigner(
                clock
        );
    }

    @Bean
    LoadDocumentArtifactUseCase loadDocumentArtifactUseCase(
            DocumentArtifactRepositoryPort repositoryPort,
            DocumentArtifactStoragePort storagePort
    ) {

        return new LoadDocumentArtifactService(
                repositoryPort,
                storagePort
        );
    }

    @Bean
    SignFiscalDocumentUseCase signFiscalDocumentUseCase(
            LoadDocumentArtifactUseCase loadDocumentArtifactUseCase,
            StoreDocumentArtifactUseCase storeDocumentArtifactUseCase,
            SigningKeyMaterialPort signingKeyMaterialPort,
            XmlSignerPort xmlSignerPort,
            InvoiceXmlValidatorPort invoiceXmlValidatorPort,
            Clock clock
    ) {

        return new SignFiscalDocumentService(
                loadDocumentArtifactUseCase,
                storeDocumentArtifactUseCase,
                signingKeyMaterialPort,
                xmlSignerPort,
                invoiceXmlValidatorPort,
                clock
        );
    }

    @Bean
    MarkDocumentSignedUseCase markDocumentSignedUseCase(
            DocumentRepository documentRepository,
            LoadDocumentArtifactUseCase artifactLoader,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {

        MarkDocumentSignedService service =
                new MarkDocumentSignedService(
                        documentRepository,
                        clock,
                        artifactLoader,
                        new io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml.JdkSriXadesSignatureVerifier()
                );

        return new TransactionalMarkDocumentSignedUseCase(
                service,
                new TransactionTemplate(
                        transactionManager
                )
        );
    }

    @Bean
    SignAndFinalizeFiscalDocumentUseCase
    signAndFinalizeFiscalDocumentUseCase(
            SignFiscalDocumentUseCase signFiscalDocumentUseCase,
            MarkDocumentSignedUseCase markDocumentSignedUseCase
    ) {

        return new SignAndFinalizeFiscalDocumentService(
                signFiscalDocumentUseCase,
                markDocumentSignedUseCase
        );
    }
}