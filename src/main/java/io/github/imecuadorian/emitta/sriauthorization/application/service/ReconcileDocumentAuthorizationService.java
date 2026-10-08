package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;

import io.github.imecuadorian.emitta.sriauthorization.application.port.in.MarkDocumentAuthorizedUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.QueryDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.StoreAuthorizedDocumentXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.VerifyAuthorizedXmlUseCase;

import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import java.util.Objects;
import java.util.UUID;

public final class ReconcileDocumentAuthorizationService
        implements ReconcileDocumentAuthorizationUseCase {

    private final QueryDocumentAuthorizationUseCase
            queryDocumentAuthorizationUseCase;

    private final LoadDocumentArtifactUseCase
            loadDocumentArtifactUseCase;

    private final VerifyAuthorizedXmlUseCase
            verifyAuthorizedXmlUseCase;

    private final StoreAuthorizedDocumentXmlUseCase
            storeAuthorizedDocumentXmlUseCase;

    private final MarkDocumentAuthorizedUseCase
            markDocumentAuthorizedUseCase;

    private final XmlSignatureVerifierPort xmlSignatureVerifierPort;

    private final DocumentRepository documentRepository;

    private final SriAuthorizationEvidencePort sriAuthorizationEvidencePort;

    public ReconcileDocumentAuthorizationService(
            QueryDocumentAuthorizationUseCase queryDocumentAuthorizationUseCase,
            LoadDocumentArtifactUseCase loadDocumentArtifactUseCase,
            VerifyAuthorizedXmlUseCase verifyAuthorizedXmlUseCase,
            StoreAuthorizedDocumentXmlUseCase storeAuthorizedDocumentXmlUseCase,
            XmlSignatureVerifierPort xmlSignatureVerifierPort,
            MarkDocumentAuthorizedUseCase markDocumentAuthorizedUseCase,
            DocumentRepository documentRepository,
            SriAuthorizationEvidencePort sriAuthorizationEvidencePort
    ) {

        this.queryDocumentAuthorizationUseCase =
                Objects.requireNonNull(
                        queryDocumentAuthorizationUseCase
                );

        this.loadDocumentArtifactUseCase =
                Objects.requireNonNull(
                        loadDocumentArtifactUseCase
                );

        this.verifyAuthorizedXmlUseCase =
                Objects.requireNonNull(
                        verifyAuthorizedXmlUseCase
                );

        this.storeAuthorizedDocumentXmlUseCase =
                Objects.requireNonNull(
                        storeAuthorizedDocumentXmlUseCase
                );

        this.markDocumentAuthorizedUseCase =
                Objects.requireNonNull(
                        markDocumentAuthorizedUseCase
                );

        this.xmlSignatureVerifierPort =
                Objects.requireNonNull(
                        xmlSignatureVerifierPort
                );

        this.documentRepository =
                Objects.requireNonNull(
                        documentRepository
                );

        this.sriAuthorizationEvidencePort =
                Objects.requireNonNull(
                        sriAuthorizationEvidencePort
                );
    }

    @Override
    public SriAuthorizationResult reconcile(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        SriAuthorizationResult authorization =
                Objects.requireNonNull(
                        queryDocumentAuthorizationUseCase.query(
                                documentId
                        ),
                        "SRI authorization result cannot be null"
                );

        /*
         * A missing authorization is not a rejection.
         * Do not alter the fiscal document state.
         */
        if (
                authorization.status()
                        == SriAuthorizationStatus.NOT_FOUND
        ) {

            return authorization;
        }

        /*
         * A non-authorized response requires further
         * classification and audit before state changes.
         */
        if (
                authorization.status()
                        == SriAuthorizationStatus.NOT_AUTHORIZED
        ) {

            return authorization;
        }

        if (
                authorization.status()
                        != SriAuthorizationStatus.AUTHORIZED
        ) {

            throw new IllegalStateException(
                    "Unsupported SRI authorization status: "
                            + authorization.status()
            );
        }

        /*
         * Load the original signed XML from durable storage.
         * The existing artifact loader verifies SHA-256.
         */
        byte[] originalSignedXml =
                loadDocumentArtifactUseCase.load(
                        documentId,
                        DocumentArtifactType.SIGNED_XML
                ).content();

        /*
         * Verify the original XAdES-BES signature
         * before accepting the SRI authorization evidence.
         */
        xmlSignatureVerifierPort.verify(
                originalSignedXml
        );

        /*
         * Compare the SRI document against the original.
         * Any mismatch stops the workflow.
         */
        verifyAuthorizedXmlUseCase.verify(
                originalSignedXml,
                authorization
        );

        /*
         * Store the authorized XML before persisting
         * its fiscal metadata.
         */
        DocumentArtifact authorizedArtifact =
                Objects.requireNonNull(
                        storeAuthorizedDocumentXmlUseCase.store(
                                documentId,
                                authorization
                        ),
                        "Stored authorized XML artifact cannot be null"
                );

        /*
         * Obtain the fiscal environment from
         * the persisted document.
         */
        Document document =
                documentRepository.findById(documentId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Document not found: " + documentId
                                )
                        );

        /*
         * Build the fiscal evidence using the actual
         * authorization data returned by SRI.
         */
        SriAuthorizationEvidence evidence =
                new SriAuthorizationEvidence(
                        documentId,
                        authorization.authorizationNumber(),
                        authorization.authorizedAt(),
                        document.getEnvironment(),
                        authorizedArtifact.sha256(),
                        authorization.messages()
                );

        /*
         * Persist fiscal metadata before marking
         * the document as authorized.
         */
        Objects.requireNonNull(
                sriAuthorizationEvidencePort.save(evidence),
                "Persisted SRI authorization evidence cannot be null"
        );

        /*
         * Update the document only after the XML
         * and authorization evidence are stored.
         */
        markDocumentAuthorizedUseCase.markAuthorized(
                documentId
        );

        return authorization;

    }
}