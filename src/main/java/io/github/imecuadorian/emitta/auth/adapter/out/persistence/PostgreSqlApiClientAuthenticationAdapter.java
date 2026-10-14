package io.github.imecuadorian.emitta.auth.adapter.out.persistence;

import io.github.imecuadorian.emitta.auth.application.model.AuthenticatedApiClient;
import io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAuthenticationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class PostgreSqlApiClientAuthenticationAdapter
        implements ApiClientAuthenticationPort, io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAdministrationPort {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlApiClientAuthenticationAdapter(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
                Objects.requireNonNull(
                        jdbcTemplate
                );
    }

    @Override
    public Optional<AuthenticatedApiClient> findByClientId(
            String clientId
    ) {

        List<ApiClientRow> clients =
                jdbcTemplate.query(
                        """
                        SELECT
                            id,
                            tenant_id,
                            client_id,
                            client_secret_hash,
                            status
                        FROM emitta.api_clients
                        WHERE client_id = ?
                          AND EXISTS (SELECT 1 FROM emitta.tenants t WHERE t.id=api_clients.tenant_id AND t.status='ACTIVE')
                        """,
                        (rs, rowNum) ->
                                new ApiClientRow(
                                        rs.getObject(
                                                "id",
                                                UUID.class
                                        ),
                                        rs.getObject(
                                                "tenant_id",
                                                UUID.class
                                        ),
                                        rs.getString(
                                                "client_id"
                                        ),
                                        rs.getString(
                                                "client_secret_hash"
                                        ),
                                        rs.getString(
                                                "status"
                                        )
                                ),
                        clientId
                );

        if (clients.isEmpty()) {
            return Optional.empty();
        }

        ApiClientRow client =
                clients.getFirst();

        Set<String> scopes =
                new HashSet<>(
                        jdbcTemplate.queryForList(
                                """
                                SELECT scope
                                FROM emitta.api_client_scopes
                                WHERE api_client_id = ?
                                ORDER BY scope
                                """,
                                String.class,
                                client.id()
                        )
                );

        return Optional.of(
                new AuthenticatedApiClient(
                        client.id(),
                        client.tenantId(),
                        client.clientId(),
                        client.secretHash(),
                        client.status(),
                        scopes
                )
        );
    }

    @Override
    public void updateLastUsedAt(
            UUID apiClientId,
            Instant lastUsedAt
    ) {

        jdbcTemplate.update(
                """
                UPDATE emitta.api_clients
                SET last_used_at = ?
                WHERE id = ?
                """,
                lastUsedAt.atOffset(
                        ZoneOffset.UTC
                ),
                apiClientId
        );
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void create(UUID tenant, UUID id, String clientId, String name, String hash, Set<String> scopes) {
        var active=jdbcTemplate.queryForList("SELECT id FROM emitta.tenants WHERE id=? AND status='ACTIVE' FOR UPDATE", UUID.class,tenant);
        if(active.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Active tenant not found");
        jdbcTemplate.update("INSERT INTO emitta.api_clients(id,tenant_id,client_id,name,client_secret_hash,status) VALUES(?,?,?,?,?,'ACTIVE')",id,tenant,clientId,name,hash);
        for(String scope:scopes) jdbcTemplate.update("INSERT INTO emitta.api_client_scopes(api_client_id,scope) VALUES(?,?)",id,scope);
        AdministrationAuditRecorder.record(org.springframework.jdbc.core.simple.JdbcClient.create(jdbcTemplate),tenant,id,"CLIENT_CREATED");
    }
    @Override
    public java.util.List<ClientSummary> listClients(UUID tenant, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT c.id,c.tenant_id,c.client_id,c.name,c.status,
                       COALESCE(string_agg(s.scope,',' ORDER BY s.scope),'') AS scopes
                FROM emitta.api_clients c LEFT JOIN emitta.api_client_scopes s ON s.api_client_id=c.id
                WHERE c.tenant_id=? GROUP BY c.id ORDER BY c.id LIMIT ? OFFSET ?
                """, (rs,n) -> new ClientSummary(rs.getObject("id",UUID.class),rs.getObject("tenant_id",UUID.class),
                rs.getString("client_id"),rs.getString("name"),rs.getString("status"),
                rs.getString("scopes").isEmpty()?java.util.List.of():java.util.List.of(rs.getString("scopes").split(","))),tenant,limit,offset);
    }
    @Override
    public java.util.List<TenantSummary> listTenants(int limit, int offset) {
        return jdbcTemplate.query("SELECT id,name,status FROM emitta.tenants ORDER BY id LIMIT ? OFFSET ?",
                (rs,n) -> new TenantSummary(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("status")),limit,offset);
    }
    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<String> rotate(UUID tenant, UUID id, String hash) {
        var result=jdbcTemplate.queryForList("UPDATE emitta.api_clients SET client_secret_hash=?,rotated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='ACTIVE' AND EXISTS(SELECT 1 FROM emitta.tenants WHERE id=? AND status='ACTIVE') RETURNING client_id",String.class,hash,id,tenant,tenant).stream().findFirst();
        if(result.isPresent()) AdministrationAuditRecorder.record(org.springframework.jdbc.core.simple.JdbcClient.create(jdbcTemplate),tenant,id,"CLIENT_SECRET_ROTATED");
        return result;
    }
    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean revoke(UUID tenant, UUID id) {
        boolean result=jdbcTemplate.update("UPDATE emitta.api_clients SET status='REVOKED' WHERE id=? AND tenant_id=? AND status='ACTIVE' AND EXISTS(SELECT 1 FROM emitta.tenants WHERE id=? AND status='ACTIVE')",id,tenant,tenant)==1;
        if(result) AdministrationAuditRecorder.record(org.springframework.jdbc.core.simple.JdbcClient.create(jdbcTemplate),tenant,id,"CLIENT_REVOKED");
        return result;
    }
    private record ApiClientRow(
            UUID id,
            UUID tenantId,
            String clientId,
            String secretHash,
            String status
    ) {
    }
}
