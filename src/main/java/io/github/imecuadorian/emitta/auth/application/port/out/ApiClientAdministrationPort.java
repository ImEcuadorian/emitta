package io.github.imecuadorian.emitta.auth.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface ApiClientAdministrationPort {
    record ClientSummary(UUID id, UUID tenantId, String clientId, String name, String status, java.util.List<String> scopes) {}
    record TenantSummary(UUID id, String name, String status) {}
    java.util.List<ClientSummary> listClients(UUID tenant, int limit, int offset);
    java.util.List<TenantSummary> listTenants(int limit, int offset);
    void create(UUID tenant, UUID id, String clientId, String name, String hash, Set<String> scopes);
    java.util.Optional<String> rotate(UUID tenant, UUID id, String hash);
    boolean revoke(UUID tenant, UUID id);
}
