package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.MarkDocumentSignedUseCase;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MarkDocumentSignedService
        implements MarkDocumentSignedUseCase {

    private final DocumentRepository documentRepository;
    private final Clock clock;

    public MarkDocumentSignedService(
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
    public Document markSigned(
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
         * Idempotent RabbitMQ redelivery.
         *
         * If the document was already marked as SIGNED,
         * there is nothing else to persist.
         */
        if (
                document.getStatus()
                        == DocumentStatus.SIGNED
        ) {

            return document;
        }

        if (
                document.getStatus()
                        != DocumentStatus.GENERATING
        ) {

            throw new IllegalStateException(
                    "Cannot mark document as signed from status "
                            + document.getStatus()
            );
        }

        Instant now =
                clock.instant();

        document.markSigned(
                now
        );

        return documentRepository.save(
                document
        );
    }
}