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
        implements ApiClientAuthenticationPort {

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

    private record ApiClientRow(
            UUID id,
            UUID tenantId,
            String clientId,
            String secretHash,
            String status
    ) {
    }
}