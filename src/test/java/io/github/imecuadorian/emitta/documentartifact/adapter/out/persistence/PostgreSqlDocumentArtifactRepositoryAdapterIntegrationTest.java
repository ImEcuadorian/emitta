package io.github.imecuadorian.emitta.documentartifact.adapter.out.persistence;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgreSqlDocumentArtifactRepositoryAdapterIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "55555555-5555-5555-5555-555555555555"
            );

    private static final UUID ARTIFACT_ID =
            UUID.fromString(
                    "66666666-6666-6666-6666-666666666666"
            );

    private static final Instant CREATED_AT =
            Instant.parse(
                    "2026-10-06T23:30:00Z"
            );

    private static final String SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:18"
            );

    private static PostgreSqlDocumentArtifactRepositoryAdapter
            adapter;

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

        JdbcClient jdbcClient =
                JdbcClient.create(
                        dataSource
                );

        adapter =
                new PostgreSqlDocumentArtifactRepositoryAdapter(
                        jdbcClient
                );

        seedDocument(
                jdbcClient
        );
    }

    @Test
    void shouldPersistAndLoadDocumentArtifact() {

        DocumentArtifact artifact =
                new DocumentArtifact(
                        ARTIFACT_ID,
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML,
                        "application/xml",
                        "documents/"
                                + DOCUMENT_ID
                                + "/unsigned.xml",
                        SHA256,
                        2048,
                        CREATED_AT
                );

        DocumentArtifact saved =
                adapter.save(
                        artifact
                );

        assertEquals(
                ARTIFACT_ID,
                saved.id()
        );

        assertEquals(
                DOCUMENT_ID,
                saved.documentId()
        );

        assertEquals(
                DocumentArtifactType.UNSIGNED_XML,
                saved.type()
        );

        assertEquals(
                "application/xml",
                saved.contentType()
        );

        assertEquals(
                "documents/"
                        + DOCUMENT_ID
                        + "/unsigned.xml",
                saved.storageKey()
        );

        assertEquals(
                SHA256,
                saved.sha256()
        );

        assertEquals(
                2048,
                saved.sizeBytes()
        );

        assertEquals(
                CREATED_AT,
                saved.createdAt()
        );

        Optional<DocumentArtifact> reloaded =
                adapter.findByDocumentIdAndType(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML
                );

        assertTrue(
                reloaded.isPresent()
        );

        assertEquals(
                saved,
                reloaded.orElseThrow()
        );
    }

    @Test
    void shouldReturnEmptyWhenArtifactDoesNotExist() {

        Optional<DocumentArtifact> result =
                adapter.findByDocumentIdAndType(
                        DOCUMENT_ID,
                        DocumentArtifactType.AUTHORIZED_XML
                );

        assertTrue(
                result.isEmpty()
        );
    }

    private static void seedDocument(
            JdbcClient jdbcClient
    ) {

        jdbcClient.sql(
                        """
                        INSERT INTO tenants (
                            id,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            'Artifact Integration Tenant',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        TENANT_ID
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
                            'ARTIFACT TEST S.A.S.',
                            'ARTIFACT TEST',
                            'Quito, Ecuador',
                            'ACTIVE',
                            TRUE,
                            FALSE
                        )
                        """
                )
                .param(
                        "id",
                        TAXPAYER_ID
                )
                .param(
                        "tenantId",
                        TENANT_ID
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
                        ESTABLISHMENT_ID
                )
                .param(
                        "taxpayerId",
                        TAXPAYER_ID
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
                        POINT_OF_ISSUE_ID
                )
                .param(
                        "establishmentId",
                        ESTABLISHMENT_ID
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
                            'GENERATING',
                            'artifact-repository-test',
                            TIMESTAMPTZ '2026-10-06 23:00:00+00'
                        )
                        """
                )
                .param(
                        "id",
                        DOCUMENT_ID
                )
                .param(
                        "tenantId",
                        TENANT_ID
                )
                .param(
                        "taxpayerId",
                        TAXPAYER_ID
                )
                .param(
                        "pointOfIssueId",
                        POINT_OF_ISSUE_ID
                )
                .update();
    }
}