package io.github.imecuadorian.emitta.outbox.adapter.out.persistence;

import io.github.imecuadorian.emitta.outbox.application.model.OutboxEvent;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxEventPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

@Repository
public class PostgreSqlOutboxEventAdapter
        implements OutboxEventPort {

    private static final String INSERT_SQL = """
            INSERT INTO emitta.outbox_events (
                id,
                tenant_id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload
            )
            VALUES (
                ?, ?, ?, ?, ?, CAST(? AS jsonb)
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public PostgreSqlOutboxEventAdapter(
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper
    ) {
        this.jdbcTemplate =
                Objects.requireNonNull(
                        jdbcTemplate
                );

        this.jsonMapper =
                Objects.requireNonNull(
                        jsonMapper
                );
    }

    @Override
    public void append(
            OutboxEvent event
    ) {

        Objects.requireNonNull(
                event,
                "Outbox event cannot be null"
        );

        String payloadJson =
                jsonMapper.writeValueAsString(
                        event.payload()
                );

        jdbcTemplate.update(
                INSERT_SQL,
                event.id(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                payloadJson
        );
    }
}