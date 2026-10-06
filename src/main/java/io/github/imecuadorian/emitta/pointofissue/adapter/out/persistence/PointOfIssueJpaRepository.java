package io.github.imecuadorian.emitta.pointofissue.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PointOfIssueJpaRepository
        extends JpaRepository<PointOfIssueJpaEntity, UUID> {

    boolean existsByEstablishmentIdAndCode(
            UUID establishmentId,
            String code
    );
}