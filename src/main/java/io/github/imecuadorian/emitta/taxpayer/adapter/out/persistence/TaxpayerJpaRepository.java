package io.github.imecuadorian.emitta.taxpayer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TaxpayerJpaRepository
        extends JpaRepository<TaxpayerJpaEntity, UUID> {

    boolean existsByTenantIdAndRuc(
            UUID tenantId,
            String ruc
    );
}