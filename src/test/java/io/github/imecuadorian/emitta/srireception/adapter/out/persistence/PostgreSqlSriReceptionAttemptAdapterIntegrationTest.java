package io.github.imecuadorian.emitta.srireception.adapter.out.persistence;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttempt;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttemptResult;

import org.flywaydb.core.Flyway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class PostgreSqlSriReceptionAttemptAdapterIntegrationTest {

    private static final Instant FIRST_STARTED_AT =
            Instant.parse(
                    "2026-10-07T22:00:00Z"
            );

    private static final Instant FIRST_FINISHED_AT =
            Instant.parse(
                    "2026-10-07T22:00:02Z"
            );

    private static final Instant SECOND_STARTED_AT =
            Instant.parse(
                    "2026-10-07T22:00:10Z"
            );

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:18"
            );

    private static JdbcClient jdbcClient;

    private static PostgreSqlSriReceptionAttemptAdapter adapter;

    private UUID tenantId;
    private UUID taxpayerId;
    private UUID establishmentId;
    private UUID pointOfIssueId;
    private UUID documentId;

    @BeforeAll
    static void setUpDatabase() {

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .schemas(
                        "emitta"
                )
                .defaultSchema(
                        "emitta"
                )
                .createSchemas(
                        true
                )
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        String jdbcUrl =
                POSTGRES.getJdbcUrl();

        String separator =
                jdbcUrl.contains("?")
                        ? "&"
                        : "?";

        String emittaJdbcUrl =
                jdbcUrl
                        + separator
                        + "currentSchema=emitta";

        DataSource dataSource =
                new DriverManagerDataSource(
                        emittaJdbcUrl,
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                );

        jdbcClient =
                JdbcClient.create(
                        dataSource
                );

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(
                        dataSource
                );

        adapter =
                new PostgreSqlSriReceptionAttemptAdapter(
                        jdbcClient,
                        new TransactionTemplate(
                                transactionManager
                        )
                );
    }

    @BeforeEach
    void setUpDocument() {

        tenantId =
                UUID.randomUUID();

        taxpayerId =
                UUID.randomUUID();

        establishmentId =
                UUID.randomUUID();

        pointOfIssueId =
                UUID.randomUUID();

        documentId =
                UUID.randomUUID();

        seedDocument();
    }

    @Test
    void shouldStartFirstSriSubmissionAttempt() {

        SriReceptionAttempt attempt =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        assertEquals(
                documentId,
                attempt.documentId()
        );

        assertEquals(
                1,
                attempt.attemptNumber()
        );

        assertEquals(
                FIRST_STARTED_AT,
                attempt.startedAt()
        );

        Map<String, Object> persisted =
                findAttempt(
                        1
                );

        assertEquals(
                1,
                ((Number) persisted.get(
                        "attempt_number"
                )).intValue()
        );

        assertEquals(
                "SRI_SUBMISSION",
                persisted.get(
                        "operation"
                )
        );

        assertNull(
                persisted.get(
                        "result"
                )
        );

        assertNull(
                persisted.get(
                        "finished_at"
                )
        );
    }

    @Test
    void shouldCompleteSriSubmissionAttemptAsReceived() {

        SriReceptionAttempt attempt =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        adapter.complete(
                documentId,
                attempt.attemptNumber(),
                SriReceptionAttemptResult.RECEIVED,
                null,
                null,
                FIRST_FINISHED_AT
        );

        Map<String, Object> persisted =
                findAttempt(
                        1
                );

        assertEquals(
                "RECEIVED",
                persisted.get(
                        "result"
                )
        );

        assertNull(
                persisted.get(
                        "error_code"
                )
        );

        assertNull(
                persisted.get(
                        "error_message"
                )
        );

        assertEquals(
                FIRST_FINISHED_AT,
                ((java.sql.Timestamp) persisted.get(
                        "finished_at"
                )).toInstant()
        );
    }

    @Test
    void shouldBlockSecondSubmissionWhenFirstAttemptIsOpen() {

        SriReceptionAttempt first =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        assertEquals(
                1,
                first.attemptNumber()
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adapter.start(
                                        documentId,
                                        SECOND_STARTED_AT
                                )
                );

        assertEquals(
                "SRI submission requires reconciliation "
                        + "before another attempt for document: "
                        + documentId,
                exception.getMessage()
        );

        assertEquals(
                1,
                countAttempts()
        );
    }

    @Test
    void shouldBlockSubmissionAfterSriReceivedDocument() {

        SriReceptionAttempt first =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        adapter.complete(
                documentId,
                first.attemptNumber(),
                SriReceptionAttemptResult.RECEIVED,
                null,
                null,
                FIRST_FINISHED_AT
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        adapter.start(
                                documentId,
                                SECOND_STARTED_AT
                        )
        );

        assertEquals(
                1,
                countAttempts()
        );
    }

    @Test
    void shouldBlockAutomaticRetryAfterTechnicalError() {

        SriReceptionAttempt first =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        adapter.complete(
                documentId,
                first.attemptNumber(),
                SriReceptionAttemptResult.TECHNICAL_ERROR,
                "NETWORK_ERROR",
                "SRI connection timeout",
                FIRST_FINISHED_AT
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        adapter.start(
                                documentId,
                                SECOND_STARTED_AT
                        )
        );

        assertEquals(
                1,
                countAttempts()
        );
    }

    @Test
    void shouldAllowIdempotentCompletionWithSameResult() {

        SriReceptionAttempt attempt =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        adapter.complete(
                documentId,
                attempt.attemptNumber(),
                SriReceptionAttemptResult.RECEIVED,
                null,
                null,
                FIRST_FINISHED_AT
        );

        /*
         * Simulates duplicate completion after recovery/redelivery.
         */
        adapter.complete(
                documentId,
                attempt.attemptNumber(),
                SriReceptionAttemptResult.RECEIVED,
                null,
                null,
                FIRST_FINISHED_AT.plusSeconds(
                        5
                )
        );

        Map<String, Object> persisted =
                findAttempt(
                        1
                );

        assertEquals(
                "RECEIVED",
                persisted.get(
                        "result"
                )
        );

        /*
         * The original completion timestamp must remain unchanged.
         */
        assertEquals(
                FIRST_FINISHED_AT,
                ((java.sql.Timestamp) persisted.get(
                        "finished_at"
                )).toInstant()
        );
    }

    @Test
    void shouldRejectConflictingSecondCompletion() {

        SriReceptionAttempt attempt =
                adapter.start(
                        documentId,
                        FIRST_STARTED_AT
                );

        adapter.complete(
                documentId,
                attempt.attemptNumber(),
                SriReceptionAttemptResult.RECEIVED,
                null,
                null,
                FIRST_FINISHED_AT
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adapter.complete(
                                        documentId,
                                        attempt.attemptNumber(),
                                        SriReceptionAttemptResult.RETURNED,
                                        null,
                                        null,
                                        FIRST_FINISHED_AT.plusSeconds(
                                                5
                                        )
                                )
                );

        assertEquals(
                "SRI reception attempt was already completed with result RECEIVED",
                exception.getMessage()
        );
    }

    private Map<String, Object> findAttempt(
            int attemptNumber
    ) {

        return jdbcClient.sql(
                        """
                        SELECT
                            attempt_number,
                            operation,
                            result,
                            error_code,
                            error_message,
                            started_at,
                            finished_at
                        FROM document_attempts
                        WHERE document_id = :documentId
                          AND operation = 'SRI_SUBMISSION'
                          AND attempt_number = :attemptNumber
                        """
                )
                .param(
                        "documentId",
                        documentId
                )
                .param(
                        "attemptNumber",
                        attemptNumber
                )
                .query()
                .singleRow();
    }

    private void seedDocument() {

        jdbcClient.sql(
                        """
                        INSERT INTO tenants (
                            id,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            'SRI Reception Attempt Test',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        tenantId
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO taxpayers (
                            id,
                            tenant_id,
                            ruc,
                            legal_name,
                            trade_name,
                            main_address,
                            status,
                            test_enabled,
                            production_enabled
                        )
                        VALUES (
                            :id,
                            :tenantId,
                            '1790012345001',
                            'EMITTA SRI TEST S.A.S.',
                            'EMITTA TEST',
                            'Quito, Ecuador',
                            'ACTIVE',
                            TRUE,
                            FALSE
                        )
                        """
                )
                .param(
                        "id",
                        taxpayerId
                )
                .param(
                        "tenantId",
                        tenantId
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO establishments (
                            id,
                            taxpayer_id,
                            code,
                            name,
                            address,
                            status
                        )
                        VALUES (
                            :id,
                            :taxpayerId,
                            '001',
                            'Matriz',
                            'Quito, Ecuador',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        establishmentId
                )
                .param(
                        "taxpayerId",
                        taxpayerId
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO points_of_issue (
                            id,
                            establishment_id,
                            code,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            :establishmentId,
                            '001',
                            'Caja Principal',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        pointOfIssueId
                )
                .param(
                        "establishmentId",
                        establishmentId
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO documents (
                            id,
                            tenant_id,
                            taxpayer_id,
                            point_of_issue_id,
                            document_type,
                            environment,
                            status,
                            idempotency_key,
                            issued_at
                        )
                        VALUES (
                            :id,
                            :tenantId,
                            :taxpayerId,
                            :pointOfIssueId,
                            'INVOICE',
                            'TEST',
                            'SUBMITTED',
                            :idempotencyKey,
                            TIMESTAMPTZ '2026-10-07 21:55:00+00'
                        )
                        """
                )
                .param(
                        "id",
                        documentId
                )
                .param(
                        "tenantId",
                        tenantId
                )
                .param(
                        "taxpayerId",
                        taxpayerId
                )
                .param(
                        "pointOfIssueId",
                        pointOfIssueId
                )
                .param(
                        "idempotencyKey",
                        "sri-attempt-"
                                + documentId
                )
                .update();
    }

    private long countAttempts() {

        return jdbcClient.sql(
                        """
                        SELECT COUNT(*)
                        FROM emitta.document_attempts
                        WHERE document_id = :documentId
                          AND operation = 'SRI_SUBMISSION'
                        """
                )
                .param(
                        "documentId",
                        documentId
                )
                .query(
                        Long.class
                )
                .single();
    }
}