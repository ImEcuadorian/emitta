package io.github.imecuadorian.emitta.outbox.adapter.out.persistence;

import io.github.imecuadorian.emitta.outbox.application.model.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class PostgreSqlOutboxEventAdapterTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("emitta_db")
                    .withUsername("emitta_test")
                    .withPassword("emitta_test");

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        registry.add(
                "spring.flyway.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.flyway.user",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.flyway.password",
                POSTGRES::getPassword
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );

        registry.add(
                "spring.jpa.properties.hibernate.default_schema",
                () -> "emitta"
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PostgreSqlOutboxEventAdapter adapter;

    private UUID tenantId;

    @BeforeEach
    void setUp() {

        adapter =
                new PostgreSqlOutboxEventAdapter(
                        jdbcTemplate,
                        JsonMapper.builder()
                                .build()
                );

        tenantId =
                UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO emitta.tenants (
                    id,
                    name,
                    status
                )
                VALUES (?, ?, ?)
                """,
                tenantId,
                "Outbox Test Tenant",
                "ACTIVE"
        );
    }

    @Test
    void shouldPersistPendingOutboxEvent() {

        UUID eventId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        OutboxEvent event =
                new OutboxEvent(
                        eventId,
                        tenantId,
                        "DOCUMENT",
                        documentId,
                        "document.received.v1",
                        Map.of(
                                "documentId",
                                documentId.toString(),
                                "status",
                                "QUEUED"
                        )
                );

        adapter.append(event);

        Map<String, Object> persisted =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            id,
                            tenant_id,
                            aggregate_type,
                            aggregate_id,
                            event_type,
                            payload::text AS payload,
                            published_at,
                            attempts
                        FROM emitta.outbox_events
                        WHERE id = ?
                        """,
                        eventId
                );

        assertEquals(
                eventId,
                persisted.get("id")
        );

        assertEquals(
                tenantId,
                persisted.get("tenant_id")
        );

        assertEquals(
                "DOCUMENT",
                persisted.get(
                        "aggregate_type"
                )
        );

        assertEquals(
                documentId,
                persisted.get(
                        "aggregate_id"
                )
        );

        assertEquals(
                "document.received.v1",
                persisted.get(
                        "event_type"
                )
        );

        assertNull(
                persisted.get(
                        "published_at"
                )
        );

        assertEquals(
                0,
                ((Number) persisted.get(
                        "attempts"
                )).intValue()
        );
    }
}