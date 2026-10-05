package io.github.imecuadorian.emitta.tenant.adapter.out.persistence;

import io.github.imecuadorian.emitta.tenant.application.port.out.TenantRepository;
import io.github.imecuadorian.emitta.tenant.domain.Tenant;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TenantPersistenceAdapter
        implements TenantRepository {

    private final TenantJpaRepository repository;

    TenantPersistenceAdapter(
            TenantJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Tenant save(Tenant tenant) {

        TenantJpaEntity entity =
                TenantPersistenceMapper.toEntity(tenant);

        TenantJpaEntity saved =
                repository.save(entity);

        return TenantPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Tenant> findById(UUID id) {

        return repository
                .findById(id)
                .map(TenantPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }
}