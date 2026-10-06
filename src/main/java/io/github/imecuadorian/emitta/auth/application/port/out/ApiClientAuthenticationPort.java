package io.github.imecuadorian.emitta.auth.application.port.out;

import io.github.imecuadorian.emitta.auth.application.model.AuthenticatedApiClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ApiClientAuthenticationPort {

    Optional<AuthenticatedApiClient> findByClientId(
            String clientId
    );

    void updateLastUsedAt(
            UUID apiClientId,
            Instant lastUsedAt
    );
}