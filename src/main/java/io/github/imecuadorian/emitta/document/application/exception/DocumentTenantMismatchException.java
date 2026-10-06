package io.github.imecuadorian.emitta.document.application.exception;

import java.util.UUID;

public final class DocumentTenantMismatchException
        extends RuntimeException {

    public DocumentTenantMismatchException(
            UUID tenantId,
            UUID taxpayerTenantId
    ) {
        super(
                "Fiscal point of issue does not belong to tenant "
                        + tenantId
                        + "; actual taxpayer tenant is "
                        + taxpayerTenantId
        );
    }
}