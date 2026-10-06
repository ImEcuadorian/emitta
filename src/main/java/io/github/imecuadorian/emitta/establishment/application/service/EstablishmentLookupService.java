package io.github.imecuadorian.emitta.establishment.application.service;

import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentLookupUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.out.EstablishmentRepository;

import java.util.Objects;
import java.util.UUID;

public final class EstablishmentLookupService
        implements EstablishmentLookupUseCase {

    private final EstablishmentRepository establishmentRepository;

    public EstablishmentLookupService(
            EstablishmentRepository establishmentRepository
    ) {
        this.establishmentRepository =
                Objects.requireNonNull(establishmentRepository);
    }

    @Override
    public boolean existsById(UUID establishmentId) {

        Objects.requireNonNull(
                establishmentId,
                "Establishment id cannot be null"
        );

        return establishmentRepository.existsById(
                establishmentId
        );
    }
}