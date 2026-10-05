package io.github.imecuadorian.emitta.tenant.application.service;

import io.github.imecuadorian.emitta.tenant.application.port.in.TenantLookupUseCase;
import io.github.imecuadorian.emitta.tenant.application.port.out.TenantRepository;

import java.util.Objects;
import java.util.UUID;

public final class TenantLookupService
        implements TenantLookupUseCase {

    private final TenantRepository tenantRepository;

    public TenantLookupService(
            TenantRepository tenantRepository
    ) {
        this.tenantRepository =
                Objects.requireNonNull(tenantRepository);
    }

    @Override
    public boolean existsById(UUID tenantId) {

        Objects.requireNonNull(
                tenantId,
                "Tenant id cannot be null"
        );

        return tenantRepository.existsById(tenantId);
    }
}