package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.StoreAuthorizedDocumentXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class StoreAuthorizedDocumentXmlService
        implements StoreAuthorizedDocumentXmlUseCase {

    private final DocumentRepository documentRepository;
    private final StoreDocumentArtifactUseCase storeDocumentArtifactUseCase;

    public StoreAuthorizedDocumentXmlService(
            DocumentRepository documentRepository,
            StoreDocumentArtifactUseCase storeDocumentArtifactUseCase
    ) {
        this.documentRepository =
                Objects.requireNonNull(documentRepository);

        this.storeDocumentArtifactUseCase =
                Objects.requireNonNull(storeDocumentArtifactUseCase);
    }

    @Override
    public DocumentArtifact store(
            UUID documentId,
            SriAuthorizationResult authorization
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                authorization,
                "SRI authorization cannot be null"
        );

        if (authorization.status() != SriAuthorizationStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot store authorized XML from SRI status "
                            + authorization.status()
            );
        }

        if (authorization.authorizedAt() == null) {
            throw new IllegalStateException(
                    "SRI authorization date is missing"
            );
        }

        if (authorization.authorizedXml() == null
                || authorization.authorizedXml().isBlank()) {
            throw new IllegalStateException(
                    "SRI authorized XML is missing"
            );
        }

        Document document =
                documentRepository.findById(documentId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Document not found: " + documentId
                                )
                        );

        DocumentStatus status = document.getStatus();

        if (status != DocumentStatus.SUBMITTED
                && status != DocumentStatus.RETRY_PENDING
                && status != DocumentStatus.AUTHORIZED) {

            throw new IllegalStateException(
                    "Cannot store authorized XML from document status "
                            + status
            );
        }

        String accessKey = document.getAccessKey();

        if (!Objects.equals(
                accessKey,
                authorization.authorizationNumber()
        )) {

            throw new IllegalStateException(
                    "SRI authorization number does not match document access key"
            );
        }

        byte[] xml =
                authorization.authorizedXml()
                        .getBytes(StandardCharsets.UTF_8);

        /*
         * Verify XML structure and fiscal identity before storage.
         * Cryptographic signature verification is a separate step.
         */
        validateXmlAccessKey(xml, accessKey);

        return storeDocumentArtifactUseCase.store(
                new StoreDocumentArtifactCommand(
                        documentId,
                        DocumentArtifactType.AUTHORIZED_XML,
                        "application/xml",
                        xml
                )
        );
    }

    private static void validateXmlAccessKey(
            byte[] xml,
            String expectedAccessKey
    ) {

        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    true
            );

            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

            var parsed =
                    factory.newDocumentBuilder().parse(
                            new ByteArrayInputStream(xml)
                    );

            NodeList accessKeys =
                    parsed.getElementsByTagNameNS(
                            "*",
                            "claveAcceso"
                    );

            if (accessKeys.getLength() != 1
                    || !expectedAccessKey.equals(
                    accessKeys.item(0)
                            .getTextContent()
                            .strip()
            )) {

                throw new IllegalStateException(
                        "Authorized XML access key does not match document"
                );
            }

        } catch (
                ParserConfigurationException
                | SAXException
                | IOException exception
        ) {

            throw new IllegalStateException(
                    "Invalid SRI authorized XML",
                    exception
            );
        }
    }
}