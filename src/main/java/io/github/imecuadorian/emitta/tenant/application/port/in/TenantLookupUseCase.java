package io.github.imecuadorian.emitta.tenant.application.port.in;

import java.util.UUID;

public interface TenantLookupUseCase {

    boolean existsById(UUID tenantId);
}