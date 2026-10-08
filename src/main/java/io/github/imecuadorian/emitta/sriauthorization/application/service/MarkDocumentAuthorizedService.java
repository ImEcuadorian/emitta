package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.MarkDocumentAuthorizedUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class MarkDocumentAuthorizedService
        implements MarkDocumentAuthorizedUseCase {

    private final DocumentRepository documentRepository;
    private final SriAuthorizationEvidencePort evidencePort;
    private final Clock clock;

    public MarkDocumentAuthorizedService(
            DocumentRepository documentRepository,
            SriAuthorizationEvidencePort evidencePort,
            Clock clock
    ) {
        this.documentRepository =
                Objects.requireNonNull(documentRepository);

        this.evidencePort =
                Objects.requireNonNull(evidencePort);

        this.clock =
                Objects.requireNonNull(clock);
    }

    @Override
    public Document markAuthorized(UUID documentId) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        /*
         * The caller must execute this operation
         * through the transactional use case.
         */
        Document document =
                documentRepository.findByIdForUpdate(documentId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Document not found: " + documentId
                                )
                        );

        DocumentStatus currentStatus =
                document.getStatus();

        /*
         * Preserve idempotency for completed documents.
         */
        if (currentStatus == DocumentStatus.AUTHORIZED) {
            return document;
        }

        if (currentStatus != DocumentStatus.SUBMITTED
                && currentStatus != DocumentStatus.RETRY_PENDING) {

            throw new IllegalStateException(
                    "Cannot authorize document from status "
                            + currentStatus
            );
        }

        /*
         * Authorization cannot be finalized without
         * previously persisted SRI evidence.
         */
        SriAuthorizationEvidence evidence =
                evidencePort.findByDocumentId(documentId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "SRI authorization evidence is missing: "
                                                + documentId
                                )
                        );

        /*
         * Ensure fiscal identity consistency.
         */
        if (!documentId.equals(evidence.documentId())) {
            throw new IllegalStateException(
                    "SRI authorization evidence document mismatch"
            );
        }

        if (!Objects.equals(
                document.getAccessKey(),
                evidence.authorizationNumber()
        )) {
            throw new IllegalStateException(
                    "SRI authorization access key mismatch"
            );
        }

        if (document.getEnvironment() != evidence.environment()) {
            throw new IllegalStateException(
                    "SRI authorization environment mismatch"
            );
        }

        /*
         * SRI provides the official authorization date.
         * Emitta's clock provides the update timestamp.
         */
        document.markAuthorized(
                evidence.authorizedAt(),
                clock.instant()
        );

        return documentRepository.save(document);
    }
}