package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT document
        FROM DocumentJpaEntity document
        WHERE document.id = :id
        """)
    Optional<DocumentJpaEntity> findByIdForUpdate(
            @Param("id") UUID id
    );
}