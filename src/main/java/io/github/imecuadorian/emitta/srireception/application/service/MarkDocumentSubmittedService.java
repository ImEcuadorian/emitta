package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentSubmittedUseCase;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MarkDocumentSubmittedService
        implements MarkDocumentSubmittedUseCase {

    private final DocumentRepository documentRepository;
    private final Clock clock;

    public MarkDocumentSubmittedService(
            DocumentRepository documentRepository,
            Clock clock
    ) {

        this.documentRepository =
                Objects.requireNonNull(
                        documentRepository
                );

        this.clock =
                Objects.requireNonNull(
                        clock
                );
    }

    @Override
    public Document markSubmitted(
            UUID documentId
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Document document =
                documentRepository
                        .findByIdForUpdate(
                                documentId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Document not found: "
                                                        + documentId
                                        )
                        );

        /*
         * Idempotent recovery:
         * if the attempt was already started, do not touch
         * submitted_at again.
         */
        if (
                document.getStatus()
                        == DocumentStatus.SUBMITTED
        ) {

            return document;
        }

        DocumentStatus currentStatus =
                document.getStatus();

        if (
                currentStatus != DocumentStatus.SIGNED
                        && currentStatus != DocumentStatus.RETRY_PENDING
        ) {

            throw new IllegalStateException(
                    "Cannot mark document as submitted from status "
                            + currentStatus
            );
        }

        Instant now =
                clock.instant();

        document.markSubmitted(
                now
        );

        return documentRepository.save(
                document
        );
    }
}