package io.github.imecuadorian.emitta.tenant.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TenantJpaRepository
        extends JpaRepository<TenantJpaEntity, UUID> {
}
