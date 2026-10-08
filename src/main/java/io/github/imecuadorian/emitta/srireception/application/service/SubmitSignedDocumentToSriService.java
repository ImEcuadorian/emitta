package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitSignedDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionPort;

import java.util.Objects;
import java.util.UUID;

public final class SubmitSignedDocumentToSriService
        implements SubmitSignedDocumentToSriUseCase {

    private final DocumentRepository documentRepository;

    private final LoadDocumentArtifactUseCase
            loadDocumentArtifactUseCase;

    private final SriReceptionPort sriReceptionPort;

    public SubmitSignedDocumentToSriService(
            DocumentRepository documentRepository,
            LoadDocumentArtifactUseCase loadDocumentArtifactUseCase,
            SriReceptionPort sriReceptionPort
    ) {

        this.documentRepository =
                Objects.requireNonNull(
                        documentRepository
                );

        this.loadDocumentArtifactUseCase =
                Objects.requireNonNull(
                        loadDocumentArtifactUseCase
                );

        this.sriReceptionPort =
                Objects.requireNonNull(
                        sriReceptionPort
                );
    }

    @Override
    public SriReceptionResult submit(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Document document =
                documentRepository
                        .findById(
                                documentId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Document not found: "
                                                        + documentId
                                        )
                        );

        DocumentStatus status =
                document.getStatus();

        if (
                status != DocumentStatus.SIGNED
                        && status != DocumentStatus.SUBMITTED
        ) {

            throw new IllegalStateException(
                    "Cannot submit document to SRI from status "
                            + status
            );
        }

        LoadedDocumentArtifact signedArtifact =
                loadDocumentArtifactUseCase.load(
                        documentId,
                        DocumentArtifactType.SIGNED_XML
                );

        SriReceptionRequest request =
                new SriReceptionRequest(
                        document.getEnvironment(),
                        signedArtifact.content()
                );

        return sriReceptionPort.submit(
                request
        );
    }
}