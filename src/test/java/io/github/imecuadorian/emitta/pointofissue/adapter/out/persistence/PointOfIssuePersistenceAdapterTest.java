package io.github.imecuadorian.emitta.pointofissue.adapter.out.persistence;

import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(PointOfIssuePersistenceAdapter.class)
@Testcontainers
class PointOfIssuePersistenceAdapterTest {

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
    private PointOfIssuePersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID establishmentId;

    @BeforeEach
    void createParentRecords() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

        establishmentId =
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
                "Point Of Issue Integration Tenant",
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
                "Matriz Quito",
                "Quito",
                "ACTIVE"
        );
    }

    @Test
    void shouldSaveAndRestorePointOfIssue() {

        UUID pointOfIssueId =
                UUID.randomUUID();

        Instant createdAt =
                Instant.parse(
                        "2026-10-06T00:00:00Z"
                );

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        pointOfIssueId,
                        establishmentId,
                        new PointOfIssueCode("001"),
                        "Caja Principal",
                        createdAt
                );

        adapter.save(pointOfIssue);

        entityManager.flush();
        entityManager.clear();

        Optional<PointOfIssue> result =
                adapter.findById(
                        pointOfIssueId
                );

        assertTrue(result.isPresent());

        PointOfIssue restored =
                result.orElseThrow();

        assertEquals(
                pointOfIssueId,
                restored.getId()
        );

        assertEquals(
                establishmentId,
                restored.getEstablishmentId()
        );

        assertEquals(
                "001",
                restored.getCode().value()
        );

        assertEquals(
                "Caja Principal",
                restored.getName()
        );

        assertEquals(
                PointOfIssueStatus.ACTIVE,
                restored.getStatus()
        );

        assertEquals(
                createdAt,
                restored.getCreatedAt()
        );
    }

    @Test
    void shouldFindExistingCodeWithinEstablishment() {

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        UUID.randomUUID(),
                        establishmentId,
                        new PointOfIssueCode("001"),
                        "Caja Principal",
                        Instant.parse(
                                "2026-10-06T00:00:00Z"
                        )
                );

        adapter.save(pointOfIssue);

        entityManager.flush();
        entityManager.clear();

        boolean exists =
                adapter
                        .existsByEstablishmentIdAndCode(
                                establishmentId,
                                new PointOfIssueCode("001")
                        );

        assertTrue(exists);
    }
}