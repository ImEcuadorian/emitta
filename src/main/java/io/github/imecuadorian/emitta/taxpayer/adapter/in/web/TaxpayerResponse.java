package io.github.imecuadorian.emitta.taxpayer.adapter.in.web;

import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;

import java.time.Instant;
import java.util.UUID;

public record TaxpayerResponse(
        UUID id,
        UUID tenantId,
        String ruc,
        String legalName,
        String tradeName,
        String mainAddress,
        String status,
        boolean testEnabled,
        boolean productionEnabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaxpayerResponse from(
            Taxpayer taxpayer
    ) {
        return new TaxpayerResponse(
                taxpayer.getId(),
                taxpayer.getTenantId(),
                taxpayer.getRuc().value(),
                taxpayer.getLegalName(),
                taxpayer.getTradeName(),
                taxpayer.getMainAddress(),
                taxpayer.getStatus().name(),
                taxpayer.isTestEnabled(),
                taxpayer.isProductionEnabled(),
                taxpayer.getCreatedAt(),
                taxpayer.getUpdatedAt()
        );
    }
}