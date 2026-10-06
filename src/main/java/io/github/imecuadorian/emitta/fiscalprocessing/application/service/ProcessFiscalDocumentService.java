package io.github.imecuadorian.emitta.fiscalprocessing.application.service;

import io.github.imecuadorian.emitta.accesskey.application.command.GenerateAccessKeyCommand;
import io.github.imecuadorian.emitta.accesskey.application.model.GeneratedAccessKey;
import io.github.imecuadorian.emitta.accesskey.application.port.in.GenerateAccessKeyUseCase;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.exception.FiscalDocumentNotFoundException;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class ProcessFiscalDocumentService
        implements ProcessFiscalDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final GenerateAccessKeyUseCase generateAccessKeyUseCase;
    private final Clock clock;
    private final ZoneId issueZone;

    public ProcessFiscalDocumentService(
            DocumentRepository documentRepository,
            GenerateAccessKeyUseCase generateAccessKeyUseCase,
            Clock clock,
            ZoneId issueZone
    ) {
        this.documentRepository =
                Objects.requireNonNull(
                        documentRepository
                );

        this.generateAccessKeyUseCase =
                Objects.requireNonNull(
                        generateAccessKeyUseCase
                );

        this.clock =
                Objects.requireNonNull(
                        clock
                );

        this.issueZone =
                Objects.requireNonNull(
                        issueZone
                );
    }

    @Override
    public ProcessFiscalDocumentResult process(
            ProcessFiscalDocumentCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Process fiscal document command cannot be null"
        );

        Document document =
                documentRepository
                        .findByIdForUpdate(
                                command.documentId()
                        )
                        .orElseThrow(
                                () ->
                                        new FiscalDocumentNotFoundException(
                                                command.documentId()
                                        )
                        );

        /*
         * Duplicate RabbitMQ deliveries are expected.
         *
         * Only QUEUED documents are eligible for this
         * specific processing event.
         */
        if (
                document.getStatus()
                        != DocumentStatus.QUEUED
        ) {
            return new ProcessFiscalDocumentResult(
                    document.getId(),
                    document.getStatus(),
                    false
            );
        }

        Instant now =
                clock.instant();

        document.startGenerating(
                now
        );

        GeneratedAccessKey generated =
                generateAccessKeyUseCase.generate(
                        new GenerateAccessKeyCommand(
                                document.getPointOfIssueId(),
                                document.getDocumentType(),
                                document.getEnvironment(),
                                document.getIssuedAt()
                                        .atZone(
                                                issueZone
                                        )
                                        .toLocalDate()
                        )
                );

        document.assignFiscalIdentity(
                generated
                        .sequential()
                        .value(),
                generated
                        .accessKey()
                        .value(),
                now
        );

        Document saved =
                documentRepository.save(
                        document
                );

        return new ProcessFiscalDocumentResult(
                saved.getId(),
                saved.getStatus(),
                true
        );
    }
}