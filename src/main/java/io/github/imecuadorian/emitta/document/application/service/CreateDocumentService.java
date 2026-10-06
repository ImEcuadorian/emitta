package io.github.imecuadorian.emitta.document.application.service;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.event.DocumentReceivedEvent;
import io.github.imecuadorian.emitta.document.application.exception.DocumentEnvironmentDisabledException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentFiscalResourceInactiveException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentFiscalResourceNotFoundException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentIdempotencyConflictException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentTenantMismatchException;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalData;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.outbox.application.model.OutboxEvent;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxEventPort;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalData;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalData;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CreateDocumentService
        implements CreateDocumentUseCase {

    private final DocumentRepository documentRepository;

    private final PointOfIssueFiscalLookupUseCase
            pointOfIssueLookup;

    private final EstablishmentFiscalLookupUseCase
            establishmentLookup;

    private final TaxpayerFiscalLookupUseCase
            taxpayerLookup;

    private final Clock clock;
    private final Supplier<UUID> idGenerator;
    private final OutboxEventPort outboxEventPort;

    public CreateDocumentService(
            DocumentRepository documentRepository,
            PointOfIssueFiscalLookupUseCase pointOfIssueLookup,
            EstablishmentFiscalLookupUseCase establishmentLookup,
            TaxpayerFiscalLookupUseCase taxpayerLookup,
            OutboxEventPort outboxEventPort,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.documentRepository =
                Objects.requireNonNull(documentRepository);

        this.pointOfIssueLookup =
                Objects.requireNonNull(pointOfIssueLookup);

        this.establishmentLookup =
                Objects.requireNonNull(establishmentLookup);

        this.taxpayerLookup =
                Objects.requireNonNull(taxpayerLookup);

        this.outboxEventPort =
                Objects.requireNonNull(outboxEventPort);

        this.clock =
                Objects.requireNonNull(clock);

        this.idGenerator =
                Objects.requireNonNull(idGenerator);
    }

    @Override
    @Transactional
    public CreateDocumentResult create(
            CreateDocumentCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Create document command cannot be null"
        );

        UUID tenantId =
                Objects.requireNonNull(
                        command.tenantId(),
                        "Tenant id cannot be null"
                );

        UUID pointOfIssueId =
                Objects.requireNonNull(
                        command.pointOfIssueId(),
                        "Point of issue id cannot be null"
                );

        Objects.requireNonNull(
                command.documentType(),
                "Document type cannot be null"
        );

        FiscalEnvironment environment =
                Objects.requireNonNull(
                        command.environment(),
                        "Environment cannot be null"
                );

        Instant issuedAt =
                Objects.requireNonNull(
                                command.issuedAt(),
                                "Issued at cannot be null"
                        )
                        .truncatedTo(
                                ChronoUnit.MILLIS
                        );

        IdempotencyKey idempotencyKey =
                new IdempotencyKey(
                        command.idempotencyKey()
                );

        var existing =
                documentRepository
                        .findByTenantIdAndIdempotencyKey(
                                tenantId,
                                idempotencyKey
                        );

        if (existing.isPresent()) {
            return replay(
                    existing.orElseThrow(),
                    command,
                    issuedAt
            );
        }

        PointOfIssueFiscalData pointOfIssue =
                pointOfIssueLookup
                        .findFiscalDataById(
                                pointOfIssueId
                        )
                        .orElseThrow(
                                () ->
                                        new DocumentFiscalResourceNotFoundException(
                                                "Point of issue",
                                                pointOfIssueId
                                        )
                        );

        requireActive(
                "Point of issue",
                pointOfIssue.id(),
                pointOfIssue.active()
        );

        EstablishmentFiscalData establishment =
                establishmentLookup
                        .findFiscalDataById(
                                pointOfIssue.establishmentId()
                        )
                        .orElseThrow(
                                () ->
                                        new DocumentFiscalResourceNotFoundException(
                                                "Establishment",
                                                pointOfIssue.establishmentId()
                                        )
                        );

        requireActive(
                "Establishment",
                establishment.id(),
                establishment.active()
        );

        TaxpayerFiscalData taxpayer =
                taxpayerLookup
                        .findFiscalDataById(
                                establishment.taxpayerId()
                        )
                        .orElseThrow(
                                () ->
                                        new DocumentFiscalResourceNotFoundException(
                                                "Taxpayer",
                                                establishment.taxpayerId()
                                        )
                        );

        requireActive(
                "Taxpayer",
                taxpayer.id(),
                taxpayer.active()
        );

        if (!tenantId.equals(
                taxpayer.tenantId()
        )) {
            throw new DocumentTenantMismatchException(
                    tenantId,
                    taxpayer.tenantId()
            );
        }

        requireEnvironmentEnabled(
                taxpayer,
                environment
        );

        Instant now =
                clock.instant()
                        .truncatedTo(
                                ChronoUnit.MILLIS
                        );

        Document document =
                Document.create(
                        idGenerator.get(),
                        tenantId,
                        taxpayer.id(),
                        pointOfIssue.id(),
                        command.documentType(),
                        environment,
                        idempotencyKey,
                        issuedAt,
                        now
                );

        boolean inserted =
                documentRepository
                        .insertIfAbsent(
                                document
                        );

        if (inserted) {

            document.queue(now);

            Document queuedDocument =
                    documentRepository.save(
                            document
                    );

            DocumentReceivedEvent domainEvent =
                    new DocumentReceivedEvent(
                            queuedDocument
                    );

            OutboxEvent outboxEvent =
                    new OutboxEvent(
                            idGenerator.get(),
                            queuedDocument.getTenantId(),
                            DocumentReceivedEvent.AGGREGATE_TYPE,
                            queuedDocument.getId(),
                            DocumentReceivedEvent.EVENT_TYPE,
                            domainEvent.payload()
                    );

            outboxEventPort.append(
                    outboxEvent
            );

            return new CreateDocumentResult(
                    queuedDocument,
                    true
            );
        }
        /*
         * Another request may have inserted the same
         * tenant + idempotency key between our initial
         * lookup and the INSERT.
         */
        Document concurrentDocument =
                documentRepository
                        .findByTenantIdAndIdempotencyKey(
                                tenantId,
                                idempotencyKey
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Idempotency conflict occurred but persisted document could not be found"
                                        )
                        );

        return replay(
                concurrentDocument,
                command,
                issuedAt
        );
    }

    private CreateDocumentResult replay(
            Document existing,
            CreateDocumentCommand command,
            Instant normalizedIssuedAt
    ) {

        boolean sameRequest =
                existing.getPointOfIssueId()
                        .equals(
                                command.pointOfIssueId()
                        )
                        && existing.getDocumentType()
                        == command.documentType()
                        && existing.getEnvironment()
                        == command.environment()
                        && existing.getIssuedAt()
                        .truncatedTo(
                                ChronoUnit.MILLIS
                        )
                        .equals(
                                normalizedIssuedAt
                        );

        if (!sameRequest) {
            throw new DocumentIdempotencyConflictException(
                    existing
                            .getIdempotencyKey()
                            .value()
            );
        }

        return new CreateDocumentResult(
                existing,
                false
        );
    }

    private static void requireActive(
            String resource,
            UUID id,
            boolean active
    ) {
        if (!active) {
            throw new DocumentFiscalResourceInactiveException(
                    resource,
                    id
            );
        }
    }

    private static void requireEnvironmentEnabled(
            TaxpayerFiscalData taxpayer,
            FiscalEnvironment environment
    ) {

        boolean enabled =
                switch (environment) {

                    case TEST ->
                            taxpayer.testEnabled();

                    case PRODUCTION ->
                            taxpayer.productionEnabled();
                };

        if (!enabled) {
            throw new DocumentEnvironmentDisabledException(
                    taxpayer.id(),
                    environment
            );
        }
    }
}