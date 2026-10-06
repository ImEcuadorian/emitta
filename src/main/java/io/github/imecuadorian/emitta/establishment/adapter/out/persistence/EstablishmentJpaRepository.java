package io.github.imecuadorian.emitta.establishment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface EstablishmentJpaRepository
        extends JpaRepository<EstablishmentJpaEntity, UUID> {

    boolean existsByTaxpayerIdAndCode(
            UUID taxpayerId,
            String code
    );
}