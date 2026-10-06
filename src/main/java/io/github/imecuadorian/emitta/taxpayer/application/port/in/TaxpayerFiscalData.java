package io.github.imecuadorian.emitta.taxpayer.application.port.in;

import java.util.UUID;

public record TaxpayerFiscalData(
        UUID id,
        UUID tenantId,
        String ruc,
        boolean active,
        boolean testEnabled,
        boolean productionEnabled
) {
}