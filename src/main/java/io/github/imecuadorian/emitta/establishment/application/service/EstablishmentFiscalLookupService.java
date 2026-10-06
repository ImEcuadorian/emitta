package io.github.imecuadorian.emitta.establishment.application.service;

import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalData;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.out.EstablishmentRepository;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EstablishmentFiscalLookupService
        implements EstablishmentFiscalLookupUseCase {

    private final EstablishmentRepository repository;

    public EstablishmentFiscalLookupService(
            EstablishmentRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(repository);
    }

    @Override
    public Optional<EstablishmentFiscalData> findFiscalDataById(
            UUID establishmentId
    ) {

        Objects.requireNonNull(
                establishmentId,
                "Establishment id cannot be null"
        );

        return repository
                .findById(establishmentId)
                .map(establishment ->
                        new EstablishmentFiscalData(
                                establishment.getId(),
                                establishment.getTaxpayerId(),
                                establishment.getCode().value(),
                                establishment.getStatus()
                                        == EstablishmentStatus.ACTIVE
                        )
                );
    }
}