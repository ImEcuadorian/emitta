package io.github.imecuadorian.emitta.auth.application.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedApiClient(
        UUID id,
        UUID tenantId,
        String clientId,
        String secretHash,
        String status,
        Set<String> scopes
) {

    public AuthenticatedApiClient {

        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(secretHash);
        Objects.requireNonNull(status);
        Objects.requireNonNull(scopes);

        scopes = Set.copyOf(scopes);
    }

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}