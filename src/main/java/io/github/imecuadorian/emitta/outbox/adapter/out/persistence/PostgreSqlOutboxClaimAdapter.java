package io.github.imecuadorian.emitta.outbox.adapter.out.persistence;

import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxClaimPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class PostgreSqlOutboxClaimAdapter
        implements OutboxClaimPort {

    private final JdbcTemplate jdbcTemplate;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public PostgreSqlOutboxClaimAdapter(
            JdbcTemplate jdbcTemplate
    ) {
        this(jdbcTemplate,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PostgreSqlOutboxClaimAdapter(JdbcTemplate jdbcTemplate,io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics=metrics;
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        this.jdbcTemplate.setQueryTimeout(5);
    }

    @Override
    public List<PendingOutboxEvent> claimBatch(
            String workerId,
            int batchSize,
            Instant staleBefore
    ) {
        return metrics.timer("emitta.outbox.claim.duration").record(() -> claim(workerId,batchSize,staleBefore));
    }

    private List<PendingOutboxEvent> claim(String workerId,int batchSize,Instant staleBefore) {

        Objects.requireNonNull(
                workerId,
                "Worker id cannot be null"
        );

        Objects.requireNonNull(
                staleBefore,
                "Stale before cannot be null"
        );

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker id cannot be blank"
            );
        }

        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException(
                    "Batch size must be between 1 and 500"
            );
        }

        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT id
                    FROM emitta.outbox_events
                    WHERE published_at IS NULL
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND (
                            claimed_at IS NULL
                            OR claimed_at < ?
                      )
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE emitta.outbox_events AS event
                SET
                    claimed_at = CURRENT_TIMESTAMP,
                    claimed_by = ?,
                    attempts = event.attempts + 1
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING
                    event.id,
                    event.tenant_id,
                    event.aggregate_type,
                    event.aggregate_id,
                    event.event_type,
                    event.payload::text AS payload_json,
                    event.attempts
                """,
                (rs, rowNum) ->
                        new PendingOutboxEvent(
                                rs.getObject(
                                        "id",
                                        UUID.class
                                ),
                                rs.getObject(
                                        "tenant_id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "aggregate_type"
                                ),
                                rs.getObject(
                                        "aggregate_id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "event_type"
                                ),
                                rs.getString(
                                        "payload_json"
                                ),
                                rs.getInt(
                                        "attempts"
                                )
                        ),
                staleBefore
                        .atOffset(
                                ZoneOffset.UTC
                        ),
                batchSize,
                workerId
        );
    }

    @Override
    public java.util.Set<UUID> markPublishedBatch(List<UUID> ids,String workerId,Instant publishedAt) {
        if(ids.isEmpty())return java.util.Set.of();
        return metrics.timer("emitta.outbox.mark.duration").record(() -> {
            var marked=jdbcTemplate.query(connection -> {
                var statement=connection.prepareStatement("UPDATE emitta.outbox_events SET published_at=?,claimed_at=NULL,claimed_by=NULL,last_error=NULL WHERE id=ANY(?) AND published_at IS NULL AND claimed_by=? RETURNING id");
                statement.setQueryTimeout(5);
                statement.setObject(1,publishedAt.atOffset(ZoneOffset.UTC));
                statement.setArray(2,connection.createArrayOf("uuid",ids.toArray()));
                statement.setString(3,workerId);return statement;
            },(rs,row)->rs.getObject(1,UUID.class));
            metrics.counter("emitta.outbox.published").increment(marked.size());
            return new java.util.HashSet<>(marked);
        });
    }

    @Override
    public boolean markPublished(
            UUID eventId,
            String workerId,
            Instant publishedAt
    ) {
        boolean marked=metrics.timer("emitta.outbox.mark.duration").record(() -> mark(eventId,workerId,publishedAt));
        if(marked) metrics.counter("emitta.outbox.published").increment();
        return marked;
    }

    private boolean mark(UUID eventId,String workerId,Instant publishedAt) {

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE emitta.outbox_events
                        SET
                            published_at = ?,
                            claimed_at = NULL,
                            claimed_by = NULL,
                            last_error = NULL
                        WHERE id = ?
                          AND published_at IS NULL
                          AND claimed_by = ?
                        """,
                        publishedAt.atOffset(
                                ZoneOffset.UTC
                        ),
                        eventId,
                        workerId
                );

        return updated == 1;
    }

    @Override
    public boolean releaseAfterFailure(
            UUID eventId,
            String workerId,
            Instant nextAttemptAt,
            String error
    ) {

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE emitta.outbox_events
                        SET
                            claimed_at = NULL,
                            claimed_by = NULL,
                            next_attempt_at = ?,
                            last_error = ?
                        WHERE id = ?
                          AND published_at IS NULL
                          AND claimed_by = ?
                        """,
                        nextAttemptAt.atOffset(
                                ZoneOffset.UTC
                        ),
                        error,
                        eventId,
                        workerId
                );

        return updated == 1;
    }
}
