package io.github.imecuadorian.emitta.outbox.integration;

import io.github.imecuadorian.emitta.outbox.adapter.config.RabbitOutboxConfiguration;
import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.support.JwtTestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = {
                "emitta.outbox.publisher.enabled=false",
                "emitta.fiscal.worker.enabled=false",
                "spring.rabbitmq.publisher-confirm-type=correlated"
        }
)
@Testcontainers
class OutboxPublisherIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    "postgres:18"
            )
                    .withDatabaseName(
                            "emitta_db"
                    )
                    .withUsername(
                            "emitta_test"
                    )
                    .withPassword(
                            "emitta_test"
                    );

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(
                    "rabbitmq:4-management-alpine"
            );

    @DynamicPropertySource
    static void jwtProperties(
            DynamicPropertyRegistry registry
    ) {
        JwtTestProperties.register(
                registry
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PublishPendingOutboxUseCase
            publishPendingOutboxUseCase;

    @Test
    void shouldPublishPendingEventToRabbitMqAndMarkItPublished() {

        UUID eventId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO emitta.outbox_events (
                    id,
                    tenant_id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    payload
                )
                VALUES (
                    ?, NULL, ?, ?, ?, CAST(? AS jsonb)
                )
                """,
                eventId,
                "DOCUMENT",
                documentId,
                "document.received.v1",
                """
                {
                  "documentId": "%s",
                  "status": "QUEUED"
                }
                """.formatted(
                        documentId
                )
        );

        int published =
                publishPendingOutboxUseCase
                        .publishBatch();

        assertEquals(
                1,
                published
        );

        Message message =
                rabbitTemplate.receive(
                        RabbitOutboxConfiguration
                                .FISCAL_DOCUMENT_QUEUE,
                        5_000
                );

        assertNotNull(
                message,
                "Expected RabbitMQ message was not received"
        );

        String payload =
                new String(
                        message.getBody(),
                        StandardCharsets.UTF_8
                );

        assertTrue(
                payload.contains(
                        documentId.toString()
                )
        );

        assertEquals(
                eventId.toString(),
                message.getMessageProperties()
                        .getMessageId()
        );

        assertEquals(
                "document.received.v1",
                message.getMessageProperties()
                        .getType()
        );

        Map<String, Object> persisted =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            published_at,
                            attempts,
                            claimed_at,
                            claimed_by,
                            last_error
                        FROM emitta.outbox_events
                        WHERE id = ?
                        """,
                        eventId
                );

        assertNotNull(
                persisted.get(
                        "published_at"
                )
        );

        assertEquals(
                1,
                ((Number) persisted.get(
                        "attempts"
                )).intValue()
        );

        assertNull(
                persisted.get(
                        "claimed_at"
                )
        );

        assertNull(
                persisted.get(
                        "claimed_by"
                )
        );

        assertNull(
                persisted.get(
                        "last_error"
                )
        );
    }
}