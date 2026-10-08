package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentRejectedUseCase;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class MarkDocumentRejectedService
        implements MarkDocumentRejectedUseCase {

    private final DocumentRepository documentRepository;
    private final Clock clock;

    public MarkDocumentRejectedService(
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
    public Document markRejected(
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
                        == DocumentStatus.REJECTED
        ) {

            return document;
        }

        if (
                document.getStatus()
                        != DocumentStatus.SUBMITTED
        ) {

            throw new IllegalStateException(
                    "Cannot reject document from status "
                            + document.getStatus()
            );
        }

        document.markRejected(
                clock.instant()
        );

        return documentRepository.save(
                document
        );
    }
}