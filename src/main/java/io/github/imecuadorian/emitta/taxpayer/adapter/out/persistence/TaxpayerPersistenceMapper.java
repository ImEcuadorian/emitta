package io.github.imecuadorian.emitta.taxpayer.adapter.out.persistence;

import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;

final class TaxpayerPersistenceMapper {

    private TaxpayerPersistenceMapper() {
    }

    static TaxpayerJpaEntity toEntity(
            Taxpayer taxpayer
    ) {
        return new TaxpayerJpaEntity(
                taxpayer.getId(),
                taxpayer.getTenantId(),
                taxpayer.getRuc().value(),
                taxpayer.getLegalName(),
                taxpayer.getTradeName(),
                taxpayer.getMainAddress(),
                taxpayer.getStatus(),
                taxpayer.isTestEnabled(),
                taxpayer.isProductionEnabled(),
                taxpayer.getCreatedAt(),
                taxpayer.getUpdatedAt()
        );
    }

    static Taxpayer toDomain(
            TaxpayerJpaEntity entity
    ) {
        return Taxpayer.restore(
                entity.getId(),
                entity.getTenantId(),
                new Ruc(entity.getRuc()),
                entity.getLegalName(),
                entity.getTradeName(),
                entity.getMainAddress(),
                entity.getStatus(),
                entity.isTestEnabled(),
                entity.isProductionEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}