package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.srireception.application.port.in.ScheduleDocumentRetryUseCase;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class ScheduleDocumentRetryService
        implements ScheduleDocumentRetryUseCase {

    private final DocumentRepository documentRepository;
    private final Clock clock;

    public ScheduleDocumentRetryService(
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
    public Document scheduleRetry(
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

        if (
                document.getStatus()
                        == DocumentStatus.RETRY_PENDING
        ) {

            return document;
        }

        if (
                document.getStatus()
                        != DocumentStatus.SUBMITTED
        ) {

            throw new IllegalStateException(
                    "Cannot schedule retry from status "
                            + document.getStatus()
            );
        }

        document.scheduleRetry(
                clock.instant()
        );

        return documentRepository.save(
                document
        );
    }
}