package io.github.imecuadorian.emitta.invoicexml.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateAndStoreInvoiceXmlUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateInvoiceXmlUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;

import java.util.Objects;
import java.util.UUID;

public final class GenerateAndStoreInvoiceXmlService
        implements GenerateAndStoreInvoiceXmlUseCase {

    private static final String XML_CONTENT_TYPE =
            "application/xml";

    private final GenerateInvoiceXmlUseCase
            generateInvoiceXmlUseCase;

    private final InvoiceXmlValidatorPort
            validatorPort;

    private final StoreDocumentArtifactUseCase
            storeDocumentArtifactUseCase;

    public GenerateAndStoreInvoiceXmlService(
            GenerateInvoiceXmlUseCase generateInvoiceXmlUseCase,
            InvoiceXmlValidatorPort validatorPort,
            StoreDocumentArtifactUseCase storeDocumentArtifactUseCase
    ) {

        this.generateInvoiceXmlUseCase =
                Objects.requireNonNull(
                        generateInvoiceXmlUseCase
                );

        this.validatorPort =
                Objects.requireNonNull(
                        validatorPort
                );

        this.storeDocumentArtifactUseCase =
                Objects.requireNonNull(
                        storeDocumentArtifactUseCase
                );
    }

    @Override
    public DocumentArtifact generateAndStore(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        GeneratedInvoiceXml generated =
                generateInvoiceXmlUseCase.generate(
                        documentId
                );

        byte[] xml =
                generated.bytes();

        validatorPort.validate(
                xml
        );

        return storeDocumentArtifactUseCase.store(
                new StoreDocumentArtifactCommand(
                        documentId,
                        DocumentArtifactType.UNSIGNED_XML,
                        XML_CONTENT_TYPE,
                        xml
                )
        );
    }
}