package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface DocumentJpaRepository
        extends JpaRepository<DocumentJpaEntity, UUID> {

    Optional<DocumentJpaEntity>
    findByTenantIdAndIdempotencyKey(
            UUID tenantId,
            String idempotencyKey
    );

    Optional<DocumentJpaEntity>
    findByAccessKey(
            String accessKey
    );
}