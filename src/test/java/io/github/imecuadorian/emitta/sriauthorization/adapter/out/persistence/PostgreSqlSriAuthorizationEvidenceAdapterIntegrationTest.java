package io.github.imecuadorian.emitta.sriauthorization.adapter.out.persistence;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationMessage;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgreSqlSriAuthorizationEvidenceAdapterIntegrationTest {

    private String accessKey;

    private static final String SHA256 =
            "a".repeat(64);

    private static final Instant AUTHORIZED_AT =
            Instant.parse("2026-10-08T01:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18");

    private static JdbcClient jdbcClient;
    private static DataSource dataSource;

    private PostgreSqlSriAuthorizationEvidenceAdapter adapter;

    private UUID documentId;

    @BeforeAll
    static void setUpDatabase() {

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .schemas("emitta")
                .defaultSchema("emitta")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        String jdbcUrl = POSTGRES.getJdbcUrl();

        String separator =
                jdbcUrl.contains("?") ? "&" : "?";

        dataSource = new DriverManagerDataSource(
                jdbcUrl + separator + "currentSchema=emitta",
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        jdbcClient = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void setUp() {

        documentId = UUID.randomUUID();

        // Generate a unique 49-digit synthetic access key per test.
        accessKey = "%049d".formatted(
                new BigInteger(
                        documentId.toString().replace("-", ""),
                        16
                )
        );

        adapter =
                new PostgreSqlSriAuthorizationEvidenceAdapter(
                        jdbcClient,
                        new TransactionTemplate(
                                new DataSourceTransactionManager(
                                        dataSource
                                )
                        )
                );

        seedDocument();
    }

    @Test
    void shouldPersistAndReloadAuthorizationEvidence() {

        SriAuthorizationEvidence evidence =
                evidence(List.of(
                        message("60", "First message"),
                        message("70", "Second message")
                ));

        SriAuthorizationEvidence saved =
                adapter.save(evidence);

        SriAuthorizationEvidence reloaded =
                adapter.findByDocumentId(documentId)
                        .orElseThrow();

        assertEquals(evidence, saved);
        assertEquals(evidence, reloaded);

        assertEquals("60", reloaded.messages()
                .get(0).identifier());

        assertEquals("70", reloaded.messages()
                .get(1).identifier());

        assertEquals(1, countRows("sri_authorizations"));
        assertEquals(2, countRows("sri_authorization_messages"));
    }

    @Test
    void shouldBeIdempotentWhenSavingSameEvidenceTwice() {

        SriAuthorizationEvidence evidence =
                evidence(List.of(
                        message("60", "Warning")
                ));

        adapter.save(evidence);

        SriAuthorizationEvidence second =
                adapter.save(evidence);

        assertEquals(evidence, second);

        assertEquals(1, countRows("sri_authorizations"));
        assertEquals(1, countRows("sri_authorization_messages"));
    }

    @Test
    void shouldRejectConflictingAuthorizationEvidence() {

        SriAuthorizationEvidence original =
                evidence(List.of());

        adapter.save(original);

        SriAuthorizationEvidence conflicting =
                new SriAuthorizationEvidence(
                        documentId,
                        accessKey,
                        AUTHORIZED_AT.plusSeconds(60),
                        FiscalEnvironment.TEST,
                        SHA256,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(conflicting)
        );

        assertEquals(
                original,
                adapter.findByDocumentId(documentId)
                        .orElseThrow()
        );
    }

    @Test
    void shouldRejectEvidenceWhenArtifactIsMissing() {

        jdbcClient.sql("""
                DELETE FROM emitta.document_artifacts
                WHERE document_id = :documentId
                """)
                .param("documentId", documentId)
                .update();

        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(evidence(List.of()))
        );

        assertEquals(0, countRows("sri_authorizations"));
    }

    @Test
    void shouldRejectArtifactSha256Mismatch() {

        SriAuthorizationEvidence invalid =
                new SriAuthorizationEvidence(
                        documentId,
                        accessKey,
                        AUTHORIZED_AT,
                        FiscalEnvironment.TEST,
                        "b".repeat(64),
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(invalid)
        );

        assertEquals(0, countRows("sri_authorizations"));
    }

    @Test
    void shouldRejectDifferentDocumentAccessKey() {

        SriAuthorizationEvidence invalid =
                new SriAuthorizationEvidence(
                        documentId,
                        "0".repeat(49),
                        AUTHORIZED_AT,
                        FiscalEnvironment.TEST,
                        SHA256,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(invalid)
        );

        assertEquals(0, countRows("sri_authorizations"));
    }

    @Test
    void shouldRejectDifferentFiscalEnvironment() {

        SriAuthorizationEvidence invalid =
                new SriAuthorizationEvidence(
                        documentId,
                        accessKey,
                        AUTHORIZED_AT,
                        FiscalEnvironment.PRODUCTION,
                        SHA256,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(invalid)
        );

        assertEquals(0, countRows("sri_authorizations"));
    }

    @Test
    void shouldRollbackAllEvidenceWhenMessageInsertionFails() {

        SriAuthorizationEvidence invalid =
                evidence(List.of(
                        message("60", "Valid first message"),
                        message(
                                "X".repeat(101),
                                "Invalid second message"
                        )
                ));

        assertThrows(
                DataAccessException.class,
                () -> adapter.save(invalid)
        );

        assertEquals(0, countRows("sri_authorizations"));
        assertEquals(0, countRows("sri_authorization_messages"));
    }

    private SriAuthorizationEvidence evidence(
            List<SriAuthorizationMessage> messages
    ) {

        return new SriAuthorizationEvidence(
                documentId,
                accessKey,
                AUTHORIZED_AT,
                FiscalEnvironment.TEST,
                SHA256,
                messages
        );
    }

    private SriAuthorizationMessage message(
            String identifier,
            String description
    ) {

        return new SriAuthorizationMessage(
                identifier,
                description,
                null,
                "ADVERTENCIA"
        );
    }

    private long countRows(String table) {

        // Only fixed table names declared in these tests are permitted.
        if (!List.of(
                "sri_authorizations",
                "sri_authorization_messages"
        ).contains(table)) {

            throw new IllegalArgumentException(
                    "Unsupported test table: " + table
            );
        }

        return jdbcClient.sql(
                        "SELECT COUNT(*) FROM emitta."
                                + table
                                + " WHERE document_id = :documentId"
                )
                .param("documentId", documentId)
                .query(Long.class)
                .single();
    }

    private void seedDocument() {

        UUID tenantId = UUID.randomUUID();
        UUID taxpayerId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID pointOfIssueId = UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO emitta.tenants (
                    id, name, status
                )
                VALUES (
                    :id, 'Authorization Test Tenant', 'ACTIVE'
                )
                """)
                .param("id", tenantId)
                .update();

        jdbcClient.sql("""
                INSERT INTO emitta.taxpayers (
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
                    'EMITTA AUTHORIZATION TEST',
                    'EMITTA TEST',
                    'Quito',
                    'ACTIVE',
                    TRUE,
                    FALSE
                )
                """)
                .param("id", taxpayerId)
                .param("tenantId", tenantId)
                .update();

        jdbcClient.sql("""
                INSERT INTO emitta.establishments (
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
                    'Main',
                    'Quito',
                    'ACTIVE'
                )
                """)
                .param("id", establishmentId)
                .param("taxpayerId", taxpayerId)
                .update();

        jdbcClient.sql("""
                INSERT INTO emitta.points_of_issue (
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
                    'Main Counter',
                    'ACTIVE'
                )
                """)
                .param("id", pointOfIssueId)
                .param("establishmentId", establishmentId)
                .update();

        jdbcClient.sql("""
                INSERT INTO emitta.documents (
                    id,
                    tenant_id,
                    taxpayer_id,
                    point_of_issue_id,
                    document_type,
                    environment,
                    status,
                    access_key,
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
                    :accessKey,
                    :idempotencyKey,
                    CURRENT_TIMESTAMP
                )
                """)
                .param("id", documentId)
                .param("tenantId", tenantId)
                .param("taxpayerId", taxpayerId)
                .param("pointOfIssueId", pointOfIssueId)
                .param("accessKey", accessKey)
                .param("idempotencyKey", documentId.toString())
                .update();

        jdbcClient.sql("""
                INSERT INTO emitta.document_artifacts (
                    id,
                    document_id,
                    artifact_type,
                    content_type,
                    storage_key,
                    sha256,
                    size_bytes
                )
                VALUES (
                    :id,
                    :documentId,
                    'AUTHORIZED_XML',
                    'application/xml',
                    :storageKey,
                    :sha256,
                    128
                )
                """)
                .param("id", UUID.randomUUID())
                .param("documentId", documentId)
                .param(
                        "storageKey",
                        "documents/" + documentId + "/authorized.xml"
                )
                .param("sha256", SHA256)
                .update();
    }
}