package io.github.imecuadorian.emitta.tenant.application.port.out;

import io.github.imecuadorian.emitta.tenant.domain.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID id);

    boolean existsById(UUID id);

}