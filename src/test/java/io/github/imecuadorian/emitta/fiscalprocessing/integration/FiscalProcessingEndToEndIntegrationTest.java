package io.github.imecuadorian.emitta.fiscalprocessing.integration;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = {
                "emitta.outbox.publisher.enabled=false",
                "emitta.fiscal.worker.enabled=true",
                "spring.rabbitmq.publisher-confirm-type=correlated"
        }
)
@Testcontainers
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class FiscalProcessingEndToEndIntegrationTest {

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

    private static final Instant ISSUED_AT =
            Instant.parse(
                    "2026-10-05T20:00:00Z"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CreateDocumentUseCase createDocumentUseCase;

    @Autowired
    private PublishPendingOutboxUseCase
            publishPendingOutboxUseCase;

    private UUID tenantId;
    private UUID taxpayerId;
    private UUID establishmentId;
    private UUID pointOfIssueId;

    @BeforeEach
    void setUpFiscalHierarchy() {

        tenantId =
                UUID.randomUUID();

        taxpayerId =
                UUID.randomUUID();

        establishmentId =
                UUID.randomUUID();

        pointOfIssueId =
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
                "Fiscal E2E Tenant",
                "ACTIVE"
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.taxpayers (
                    id,
                    tenant_id,
                    ruc,
                    legal_name,
                    main_address,
                    status,
                    test_enabled,
                    production_enabled
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                taxpayerId,
                tenantId,
                "1790012345001",
                "EMITTA TEST S.A.S.",
                "Quito",
                "ACTIVE",
                true,
                false
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.establishments (
                    id,
                    taxpayer_id,
                    code,
                    name,
                    address,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                establishmentId,
                taxpayerId,
                "001",
                "Matriz",
                "Quito",
                "ACTIVE"
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.points_of_issue (
                    id,
                    establishment_id,
                    code,
                    name,
                    status
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                pointOfIssueId,
                establishmentId,
                "001",
                "Caja Principal",
                "ACTIVE"
        );
    }

    @Test
    void shouldProcessFiscalDocumentEndToEnd()
            throws InterruptedException {

        /*
         * 1. Create the fiscal document.
         *
         * This must persist:
         * - Document QUEUED
         * - pending Outbox event
         *
         * inside the same database transaction.
         */
        CreateDocumentResult created =
                createDocumentUseCase.create(
                        new CreateDocumentCommand(
                                tenantId,
                                pointOfIssueId,
                                DocumentType.INVOICE,
                                FiscalEnvironment.TEST,
                                "e2e-invoice-001",
                                ISSUED_AT
                        )
                );

        assertTrue(
                created.created()
        );

        assertEquals(
                DocumentStatus.QUEUED,
                created.document()
                        .getStatus()
        );

        UUID documentId =
                created.document()
                        .getId();

        assertNull(
                created.document()
                        .getSequential()
        );

        assertNull(
                created.document()
                        .getAccessKey()
        );

        /*
         * 2. Verify durable Outbox intent exists.
         */
        Integer pendingEvents =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM emitta.outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'document.received.v1'
                          AND published_at IS NULL
                        """,
                        Integer.class,
                        documentId
                );

        assertEquals(
                1,
                pendingEvents
        );

        /*
         * 3. Run one Outbox publication cycle manually.
         *
         * Scheduler is disabled for deterministic testing.
         */
        int published =
                publishPendingOutboxUseCase
                        .publishBatch();

        assertEquals(
                1,
                published
        );

        /*
         * 4. RabbitMQ delivery and listener execution are
         * asynchronous. Wait until the worker commits the
         * fiscal identity.
         */
        Map<String, Object> processed =
                waitUntilProcessed(
                        documentId
                );

        /*
         * 5. The worker must transition the document.
         */
        assertEquals(
                "GENERATING",
                processed.get(
                        "status"
                )
        );

        assertNotNull(
                processed.get(
                        "processing_started_at"
                )
        );

        /*
         * 6. First sequential for this scope must be 1.
         */
        assertEquals(
                1L,
                ((Number) processed.get(
                        "sequential"
                )).longValue()
        );

        /*
         * 7. SRI access key must exist and contain 49 digits.
         */
        String accessKey =
                (String) processed.get(
                        "access_key"
                );

        assertNotNull(
                accessKey
        );

        assertTrue(
                accessKey.matches(
                        "\\d{49}"
                )
        );

        /*
         * 8. PostgreSQL sequence source of truth must agree.
         */
        Long currentSequence =
                jdbcTemplate.queryForObject(
                        """
                        SELECT current_value
                        FROM emitta.document_sequences
                        WHERE point_of_issue_id = ?
                          AND document_type = 'INVOICE'
                          AND environment = 'TEST'
                        """,
                        Long.class,
                        pointOfIssueId
                );

        assertEquals(
                1L,
                currentSequence
        );

        /*
         * 9. The Outbox row must be acknowledged as published.
         */
        Map<String, Object> outbox =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            published_at,
                            attempts,
                            claimed_at,
                            claimed_by,
                            last_error
                        FROM emitta.outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'document.received.v1'
                        """,
                        documentId
                );

        assertNotNull(
                outbox.get(
                        "published_at"
                )
        );

        assertEquals(
                1,
                ((Number) outbox.get(
                        "attempts"
                )).intValue()
        );

        assertNull(
                outbox.get(
                        "claimed_at"
                )
        );

        assertNull(
                outbox.get(
                        "claimed_by"
                )
        );

        assertNull(
                outbox.get(
                        "last_error"
                )
        );
    }

    private Map<String, Object> waitUntilProcessed(
            UUID documentId
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + 10_000_000_000L;

        Map<String, Object> lastState =
                null;

        while (
                System.nanoTime()
                        < deadline
        ) {

            lastState =
                    jdbcTemplate.queryForMap(
                            """
                            SELECT
                                status,
                                sequential,
                                access_key,
                                processing_started_at
                            FROM emitta.documents
                            WHERE id = ?
                            """,
                            documentId
                    );

            boolean processed =
                    "GENERATING".equals(
                            lastState.get(
                                    "status"
                            )
                    )
                            && lastState.get(
                            "sequential"
                    ) != null
                            && lastState.get(
                            "access_key"
                    ) != null;

            if (processed) {
                return lastState;
            }

            Thread.sleep(
                    100
            );
        }

        fail(
                "Fiscal document was not processed within 10 seconds. Last state: "
                        + lastState
        );

        throw new IllegalStateException(
                "Unreachable"
        );
    }
}