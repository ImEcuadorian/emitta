package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactNotFoundException;
import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import io.github.imecuadorian.emitta.fiscalsigning.application.model.ResolvedSigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SignedXml;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.SigningKeyMaterialPort;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignerPort;
import io.github.imecuadorian.emitta.invoicexml.application.port.out.InvoiceXmlValidatorPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SignFiscalDocumentService
        implements SignFiscalDocumentUseCase {

    private static final String XML_CONTENT_TYPE =
            "application/xml";

    private final LoadDocumentArtifactUseCase
            loadDocumentArtifactUseCase;

    private final StoreDocumentArtifactUseCase
            storeDocumentArtifactUseCase;

    private final SigningKeyMaterialPort
            signingKeyMaterialPort;

    private final XmlSignerPort
            xmlSignerPort;

    private final InvoiceXmlValidatorPort invoiceXmlValidatorPort;

    private final Clock clock;

    public SignFiscalDocumentService(
            LoadDocumentArtifactUseCase loadDocumentArtifactUseCase,
            StoreDocumentArtifactUseCase storeDocumentArtifactUseCase,
            SigningKeyMaterialPort signingKeyMaterialPort,
            XmlSignerPort xmlSignerPort,
            InvoiceXmlValidatorPort invoiceXmlValidatorPort,
            Clock clock
    ) {

        this.loadDocumentArtifactUseCase =
                Objects.requireNonNull(
                        loadDocumentArtifactUseCase
                );

        this.storeDocumentArtifactUseCase =
                Objects.requireNonNull(
                        storeDocumentArtifactUseCase
                );

        this.signingKeyMaterialPort =
                Objects.requireNonNull(
                        signingKeyMaterialPort
                );

        this.xmlSignerPort =
                Objects.requireNonNull(
                        xmlSignerPort
                );

        this.invoiceXmlValidatorPort =
                Objects.requireNonNull(
                        invoiceXmlValidatorPort
                );

        this.clock =
                Objects.requireNonNull(
                        clock
                );
    }

    @Override
    public DocumentArtifact sign(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        DocumentArtifact existingSignedArtifact =
                findExistingSignedArtifact(
                        documentId
                );

        if (existingSignedArtifact != null) {

            return existingSignedArtifact;
        }

        LoadedDocumentArtifact unsignedArtifact =
                loadDocumentArtifactUseCase.load(
                        documentId,
                        DocumentArtifactType.UNSIGNED_XML
                );

        Instant signingTime =
                clock.instant();

        ResolvedSigningKeyMaterial resolvedKeyMaterial =
                signingKeyMaterialPort.loadForDocument(
                        documentId,
                        signingTime
                );

        SignedXml signedXml;
        try (var material = resolvedKeyMaterial.keyMaterial()) {
            signedXml =
                xmlSignerPort.sign(
                        unsignedArtifact.content(),
                        material
                );
        }

        byte[] signedContent =
                signedXml.content();

        /*
         * The final artifact sent to SRI must still conform
         * to the official invoice schema after XAdES insertion.
         */
        invoiceXmlValidatorPort.validate(
                signedContent
        );

        return storeDocumentArtifactUseCase.store(
                new StoreDocumentArtifactCommand(
                        documentId,
                        DocumentArtifactType.SIGNED_XML,
                        XML_CONTENT_TYPE,
                        signedContent
                )
        );
    }

    private DocumentArtifact findExistingSignedArtifact(
            UUID documentId
    ) {

        try {

            return loadDocumentArtifactUseCase
                    .load(
                            documentId,
                            DocumentArtifactType.SIGNED_XML
                    )
                    .artifact();

        } catch (DocumentArtifactNotFoundException exception) {

            return null;
        }
    }
}
