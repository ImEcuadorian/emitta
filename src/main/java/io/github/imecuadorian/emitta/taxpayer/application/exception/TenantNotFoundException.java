package io.github.imecuadorian.emitta.taxpayer.application.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public final class TenantNotFoundException
        extends RuntimeException {

    private final UUID tenantId;

    public TenantNotFoundException(UUID tenantId) {
        super(
                "Tenant not found: " + tenantId
        );

        this.tenantId = tenantId;
    }

}