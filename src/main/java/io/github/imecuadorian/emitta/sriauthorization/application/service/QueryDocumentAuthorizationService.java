package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.QueryDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationPort;

import java.util.Objects;
import java.util.UUID;

public final class QueryDocumentAuthorizationService
        implements QueryDocumentAuthorizationUseCase {

    private final DocumentRepository documentRepository;
    private final SriAuthorizationPort sriAuthorizationPort;

    public QueryDocumentAuthorizationService(
            DocumentRepository documentRepository,
            SriAuthorizationPort sriAuthorizationPort
    ) {

        this.documentRepository =
                Objects.requireNonNull(documentRepository);

        this.sriAuthorizationPort =
                Objects.requireNonNull(sriAuthorizationPort);
    }

    @Override
    public SriAuthorizationResult query(UUID documentId) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Document document =
                documentRepository.findById(documentId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Document not found: " + documentId
                                )
                        );

        DocumentStatus status = document.getStatus();

        /*
         * Only documents already submitted to SRI,
         * or awaiting recovery, can be reconciled.
         */
        if (status != DocumentStatus.SUBMITTED
                && status != DocumentStatus.RETRY_PENDING
                && status != DocumentStatus.AUTHORIZED) {

            throw new IllegalStateException(
                    "Cannot query SRI authorization from status "
                            + status
            );
        }

        SriAuthorizationRequest request =
                new SriAuthorizationRequest(
                        document.getEnvironment(),
                        document.getAccessKey()
                );

        /*
         * Read-only operation.
         * Do not change the document status here.
         */
        return Objects.requireNonNull(
                sriAuthorizationPort.query(request),
                "SRI authorization result cannot be null"
        );
    }
}