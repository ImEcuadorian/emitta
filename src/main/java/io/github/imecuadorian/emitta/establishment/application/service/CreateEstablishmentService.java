package io.github.imecuadorian.emitta.establishment.application.service;

import io.github.imecuadorian.emitta.establishment.application.command.CreateEstablishmentCommand;
import io.github.imecuadorian.emitta.establishment.application.exception.EstablishmentAlreadyExistsException;
import io.github.imecuadorian.emitta.establishment.application.exception.TaxpayerNotFoundException;
import io.github.imecuadorian.emitta.establishment.application.port.in.CreateEstablishmentUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.out.EstablishmentRepository;
import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerLookupUseCase;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CreateEstablishmentService
        implements CreateEstablishmentUseCase {

    private final EstablishmentRepository establishmentRepository;
    private final TaxpayerLookupUseCase taxpayerLookup;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public CreateEstablishmentService(
            EstablishmentRepository establishmentRepository,
            TaxpayerLookupUseCase taxpayerLookup,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.establishmentRepository =
                Objects.requireNonNull(
                        establishmentRepository
                );

        this.taxpayerLookup =
                Objects.requireNonNull(
                        taxpayerLookup
                );

        this.clock =
                Objects.requireNonNull(clock);

        this.idGenerator =
                Objects.requireNonNull(idGenerator);
    }

    @Override
    public Establishment create(
            CreateEstablishmentCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Create establishment command cannot be null"
        );

        UUID taxpayerId =
                Objects.requireNonNull(
                        command.taxpayerId(),
                        "Taxpayer id cannot be null"
                );

        if (!taxpayerLookup.existsById(taxpayerId)) {
            throw new TaxpayerNotFoundException(
                    taxpayerId
            );
        }

        EstablishmentCode code =
                new EstablishmentCode(
                        command.code()
                );

        if (
                establishmentRepository
                        .existsByTaxpayerIdAndCode(
                                taxpayerId,
                                code
                        )
        ) {
            throw new EstablishmentAlreadyExistsException(
                    code.value()
            );
        }

        Instant now =
                clock.instant();

        Establishment establishment =
                Establishment.create(
                        idGenerator.get(),
                        taxpayerId,
                        code,
                        command.name(),
                        command.address(),
                        now
                );

        return establishmentRepository.save(
                establishment
        );
    }
}