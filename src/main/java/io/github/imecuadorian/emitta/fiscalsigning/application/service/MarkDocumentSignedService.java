package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.MarkDocumentSignedUseCase;

import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MarkDocumentSignedService
        implements MarkDocumentSignedUseCase {

    private final DocumentRepository documentRepository;
    private final Clock clock;
    private final LoadDocumentArtifactUseCase artifactLoader;
    private final XmlSignatureVerifierPort signatureVerifier;

    public MarkDocumentSignedService(
            DocumentRepository documentRepository,
            Clock clock,
            LoadDocumentArtifactUseCase artifactLoader,
            XmlSignatureVerifierPort signatureVerifier
    ) {

        this.artifactLoader = Objects.requireNonNull(artifactLoader);
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier);
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

        // Revalidate the durably stored bytes before the transition, including recovery/redelivery.
        signatureVerifier.verify(artifactLoader.load(documentId, DocumentArtifactType.SIGNED_XML).content());

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