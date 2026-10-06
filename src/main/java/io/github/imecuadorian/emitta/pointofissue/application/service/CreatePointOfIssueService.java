package io.github.imecuadorian.emitta.pointofissue.application.service;

import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.command.CreatePointOfIssueCommand;
import io.github.imecuadorian.emitta.pointofissue.application.exception.EstablishmentNotFoundException;
import io.github.imecuadorian.emitta.pointofissue.application.exception.PointOfIssueAlreadyExistsException;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.CreatePointOfIssueUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.out.PointOfIssueRepository;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CreatePointOfIssueService
        implements CreatePointOfIssueUseCase {

    private final PointOfIssueRepository pointOfIssueRepository;
    private final EstablishmentLookupUseCase establishmentLookup;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public CreatePointOfIssueService(
            PointOfIssueRepository pointOfIssueRepository,
            EstablishmentLookupUseCase establishmentLookup,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.pointOfIssueRepository =
                Objects.requireNonNull(pointOfIssueRepository);

        this.establishmentLookup =
                Objects.requireNonNull(establishmentLookup);

        this.clock =
                Objects.requireNonNull(clock);

        this.idGenerator =
                Objects.requireNonNull(idGenerator);
    }

    @Override
    public PointOfIssue create(
            CreatePointOfIssueCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Create point of issue command cannot be null"
        );

        UUID establishmentId =
                Objects.requireNonNull(
                        command.establishmentId(),
                        "Establishment id cannot be null"
                );

        if (!establishmentLookup.existsById(establishmentId)) {
            throw new EstablishmentNotFoundException(
                    establishmentId
            );
        }

        PointOfIssueCode code =
                new PointOfIssueCode(
                        command.code()
                );

        if (
                pointOfIssueRepository
                        .existsByEstablishmentIdAndCode(
                                establishmentId,
                                code
                        )
        ) {
            throw new PointOfIssueAlreadyExistsException(
                    code.value()
            );
        }

        Instant now =
                clock.instant();

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        idGenerator.get(),
                        establishmentId,
                        code,
                        command.name(),
                        now
                );

        return pointOfIssueRepository.save(
                pointOfIssue
        );
    }
}