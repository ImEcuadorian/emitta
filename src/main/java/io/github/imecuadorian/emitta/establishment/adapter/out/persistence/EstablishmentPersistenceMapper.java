package io.github.imecuadorian.emitta.establishment.adapter.out.persistence;

import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;

final class EstablishmentPersistenceMapper {

    private EstablishmentPersistenceMapper() {
    }

    static EstablishmentJpaEntity toEntity(
            Establishment establishment
    ) {
        return new EstablishmentJpaEntity(
                establishment.getId(),
                establishment.getTaxpayerId(),
                establishment.getCode().value(),
                establishment.getName(),
                establishment.getAddress(),
                establishment.getStatus(),
                establishment.getCreatedAt(),
                establishment.getUpdatedAt()
        );
    }

    static Establishment toDomain(
            EstablishmentJpaEntity entity
    ) {
        return Establishment.restore(
                entity.getId(),
                entity.getTaxpayerId(),
                new EstablishmentCode(
                        entity.getCode()
                ),
                entity.getName(),
                entity.getAddress(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}