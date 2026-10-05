package io.github.imecuadorian.emitta.tenant.adapter.out.persistence;

import io.github.imecuadorian.emitta.tenant.domain.Tenant;
import org.jspecify.annotations.NonNull;

final class TenantPersistenceMapper {

    private TenantPersistenceMapper() {
    }

    static @NonNull TenantJpaEntity toEntity(@NonNull Tenant tenant) {

        return new TenantJpaEntity(
                tenant.getId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }

    static @NonNull Tenant toDomain(@NonNull TenantJpaEntity entity) {

        return Tenant.restore(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}