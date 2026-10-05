package io.github.imecuadorian.emitta.taxpayer.adapter.out.persistence;

import io.github.imecuadorian.emitta.taxpayer.application.port.out.TaxpayerRepository;
import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TaxpayerPersistenceAdapter
        implements TaxpayerRepository {

    private final TaxpayerJpaRepository repository;

    TaxpayerPersistenceAdapter(
            TaxpayerJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Taxpayer save(Taxpayer taxpayer) {

        TaxpayerJpaEntity entity =
                TaxpayerPersistenceMapper.toEntity(
                        taxpayer
                );

        TaxpayerJpaEntity saved =
                repository.save(entity);

        return TaxpayerPersistenceMapper.toDomain(
                saved
        );
    }

    @Override
    public Optional<Taxpayer> findById(
            UUID id
    ) {
        return repository
                .findById(id)
                .map(
                        TaxpayerPersistenceMapper::toDomain
                );
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public boolean existsByTenantIdAndRuc(
            UUID tenantId,
            Ruc ruc
    ) {
        return repository.existsByTenantIdAndRuc(
                tenantId,
                ruc.value()
        );
    }
}