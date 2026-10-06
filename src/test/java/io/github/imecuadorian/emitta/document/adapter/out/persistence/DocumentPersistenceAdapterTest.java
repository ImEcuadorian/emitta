package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(DocumentPersistenceAdapter.class)
@Testcontainers
class DocumentPersistenceAdapterTest {

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
    private DocumentPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID taxpayerId;
    private UUID pointOfIssueId;

    @BeforeEach
    void createFiscalHierarchy() {

        tenantId = UUID.randomUUID();
        taxpayerId = UUID.randomUUID();

        UUID establishmentId =
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
                "Document Test Tenant",
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taxpayerId,
                tenantId,
                "1790012345001",
                "NEXORF S.A.S.",
                "Quito",
                "ACTIVE",
                true,
                true
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
    void shouldSaveAndRestoreDocument() {

        Instant now =
                Instant.parse(
                        "2026-10-06T02:00:00Z"
                );

        UUID documentId =
                UUID.randomUUID();

        Document document =
                Document.create(
                        documentId,
                        tenantId,
                        taxpayerId,
                        pointOfIssueId,
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        new IdempotencyKey(
                                "order-2026-001"
                        ),
                        now,
                        now
                );

        document.assignFiscalIdentity(
                124L,
                "2111202405176001321000110010010000001241234567810",
                now.plusSeconds(1)
        );

        adapter.save(document);

        entityManager.flush();
        entityManager.clear();

        Optional<Document> result =
                adapter.findById(
                        documentId
                );

        assertTrue(
                result.isPresent()
        );

        Document restored =
                result.orElseThrow();

        assertEquals(
                documentId,
                restored.getId()
        );

        assertEquals(
                tenantId,
                restored.getTenantId()
        );

        assertEquals(
                taxpayerId,
                restored.getTaxpayerId()
        );

        assertEquals(
                pointOfIssueId,
                restored.getPointOfIssueId()
        );

        assertEquals(
                DocumentType.INVOICE,
                restored.getDocumentType()
        );

        assertEquals(
                FiscalEnvironment.TEST,
                restored.getEnvironment()
        );

        assertEquals(
                124L,
                restored.getSequential()
        );

        assertEquals(
                "2111202405176001321000110010010000001241234567810",
                restored.getAccessKey()
        );

        assertEquals(
                DocumentStatus.RECEIVED,
                restored.getStatus()
        );
    }

    @Test
    void shouldFindDocumentByIdempotencyKey() {

        Instant now =
                Instant.parse(
                        "2026-10-06T02:00:00Z"
                );

        Document document =
                Document.create(
                        UUID.randomUUID(),
                        tenantId,
                        taxpayerId,
                        pointOfIssueId,
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        new IdempotencyKey(
                                "pos-sale-9001"
                        ),
                        now,
                        now
                );

        adapter.save(document);

        entityManager.flush();
        entityManager.clear();

        Optional<Document> result =
                adapter
                        .findByTenantIdAndIdempotencyKey(
                                tenantId,
                                new IdempotencyKey(
                                        "pos-sale-9001"
                                )
                        );

        assertTrue(
                result.isPresent()
        );

        assertEquals(
                "pos-sale-9001",
                result.orElseThrow()
                        .getIdempotencyKey()
                        .value()
        );
    }

    @Test
    void shouldRejectDuplicatedTenantIdempotencyKey() {

        Instant now =
                Instant.parse(
                        "2026-10-06T02:00:00Z"
                );

        insertDocument(
                UUID.randomUUID(),
                "duplicate-key",
                now
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertDocument(
                        UUID.randomUUID(),
                        "duplicate-key",
                        now
                )
        );
    }

    private void insertDocument(
            UUID documentId,
            String idempotencyKey,
            Instant now
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO emitta.documents (
                    id,
                    tenant_id,
                    taxpayer_id,
                    point_of_issue_id,
                    document_type,
                    environment,
                    status,
                    idempotency_key,
                    issued_at,
                    received_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                documentId,
                tenantId,
                taxpayerId,
                pointOfIssueId,
                "INVOICE",
                "TEST",
                "RECEIVED",
                idempotencyKey,
                now,
                now,
                now,
                now
        );
    }
}