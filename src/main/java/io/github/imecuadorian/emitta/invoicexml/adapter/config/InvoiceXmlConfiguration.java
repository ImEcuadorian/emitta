package io.github.imecuadorian.emitta.invoicexml.adapter.config;

import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactRepositoryPort;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import io.github.imecuadorian.emitta.documentartifact.application.service.StoreDocumentArtifactService;

import io.github.imecuadorian.emitta.invoicexml.adapter.out.xml.SriInvoiceXmlGenerator;
import io.github.imecuadorian.emitta.invoicexml.adapter.out.xml.SriInvoiceXsdValidator;

import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateAndStoreInvoiceXmlUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateInvoiceXmlUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlSourcePort;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;

import io.github.imecuadorian.emitta.invoicexml.application.service.GenerateAndStoreInvoiceXmlService;
import io.github.imecuadorian.emitta.invoicexml.application.service.GenerateInvoiceXmlService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@ConditionalOnProperty(
        prefix = "emitta.artifacts.storage",
        name = "enabled",
        havingValue = "true"
)
public class InvoiceXmlConfiguration {

    @Bean
    InvoiceXmlValidatorPort invoiceXmlValidatorPort() {

        return new SriInvoiceXsdValidator();
    }

    @Bean
    GenerateInvoiceXmlUseCase generateInvoiceXmlUseCase(
            InvoiceXmlSourcePort sourcePort,
            @Value("${emitta.fiscal.provider-ruc}")
            String providerRuc,
            @Value("${emitta.fiscal.issue-zone}")
            String issueZone
    ) {

        return new GenerateInvoiceXmlService(
                sourcePort,
                new SriInvoiceXmlGenerator(),
                providerRuc,
                ZoneId.of(
                        issueZone
                )
        );
    }

    @Bean
    StoreDocumentArtifactUseCase storeDocumentArtifactUseCase(
            DocumentArtifactRepositoryPort repositoryPort,
            DocumentArtifactStoragePort storagePort
    ) {

        return new StoreDocumentArtifactService(
                repositoryPort,
                storagePort,
                Clock.systemUTC()
        );
    }

    @Bean
    GenerateAndStoreInvoiceXmlUseCase generateAndStoreInvoiceXmlUseCase(
            GenerateInvoiceXmlUseCase generateInvoiceXmlUseCase,
            InvoiceXmlValidatorPort validatorPort,
            StoreDocumentArtifactUseCase storeDocumentArtifactUseCase
    ) {

        return new GenerateAndStoreInvoiceXmlService(
                generateInvoiceXmlUseCase,
                validatorPort,
                storeDocumentArtifactUseCase
        );
    }
}