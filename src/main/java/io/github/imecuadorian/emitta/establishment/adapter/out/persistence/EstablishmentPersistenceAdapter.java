package io.github.imecuadorian.emitta.establishment.adapter.out.persistence;

import io.github.imecuadorian.emitta.establishment.application.port.out.EstablishmentRepository;
import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class EstablishmentPersistenceAdapter
        implements EstablishmentRepository {

    private final EstablishmentJpaRepository repository;

    EstablishmentPersistenceAdapter(
            EstablishmentJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Establishment save(
            Establishment establishment
    ) {

        EstablishmentJpaEntity entity =
                EstablishmentPersistenceMapper.toEntity(
                        establishment
                );

        EstablishmentJpaEntity saved =
                repository.save(entity);

        return EstablishmentPersistenceMapper.toDomain(
                saved
        );
    }

    @Override
    public Optional<Establishment> findById(
            UUID id
    ) {
        return repository
                .findById(id)
                .map(
                        EstablishmentPersistenceMapper::toDomain
                );
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public boolean existsByTaxpayerIdAndCode(
            UUID taxpayerId,
            EstablishmentCode code
    ) {
        return repository.existsByTaxpayerIdAndCode(
                taxpayerId,
                code.value()
        );
    }
}