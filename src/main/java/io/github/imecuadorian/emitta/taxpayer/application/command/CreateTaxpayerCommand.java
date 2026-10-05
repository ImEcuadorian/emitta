package io.github.imecuadorian.emitta.taxpayer.application.command;

import java.util.UUID;

public record CreateTaxpayerCommand(
        UUID tenantId,
        String ruc,
        String legalName,
        String tradeName,
        String mainAddress
) {
}