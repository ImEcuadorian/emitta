package io.github.imecuadorian.emitta.taxpayer.adapter.out.persistence;

import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;
import io.github.imecuadorian.emitta.taxpayer.domain.TaxpayerStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(TaxpayerPersistenceAdapter.class)
@Testcontainers
class TaxpayerPersistenceAdapterTest {

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
    private TaxpayerPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;

    @BeforeEach
    void createTenant() {

        tenantId = UUID.randomUUID();

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
                "Integration Test Tenant",
                "ACTIVE"
        );
    }

    @Test
    void shouldSaveAndRestoreTaxpayer() {

        UUID taxpayerId =
                UUID.randomUUID();

        Instant createdAt =
                Instant.parse(
                        "2026-10-05T15:00:00Z"
                );

        Taxpayer taxpayer =
                Taxpayer.create(
                        taxpayerId,
                        tenantId,
                        new Ruc("1790012345001"),
                        "NEXORF S.A.S.",
                        "NEXORF",
                        "Quito",
                        createdAt
                );

        adapter.save(taxpayer);

        entityManager.flush();
        entityManager.clear();

        Optional<Taxpayer> result =
                adapter.findById(taxpayerId);

        assertTrue(result.isPresent());

        Taxpayer restored =
                result.orElseThrow();

        assertEquals(
                taxpayerId,
                restored.getId()
        );

        assertEquals(
                tenantId,
                restored.getTenantId()
        );

        assertEquals(
                "1790012345001",
                restored.getRuc().value()
        );

        assertEquals(
                "NEXORF S.A.S.",
                restored.getLegalName()
        );

        assertEquals(
                "NEXORF",
                restored.getTradeName()
        );

        assertEquals(
                "Quito",
                restored.getMainAddress()
        );

        assertEquals(
                TaxpayerStatus.ACTIVE,
                restored.getStatus()
        );

        assertTrue(
                restored.isTestEnabled()
        );

        assertFalse(
                restored.isProductionEnabled()
        );

        assertEquals(
                createdAt,
                restored.getCreatedAt()
        );
    }

    @Test
    void shouldFindExistingRucWithinTenant() {

        Taxpayer taxpayer =
                Taxpayer.create(
                        UUID.randomUUID(),
                        tenantId,
                        new Ruc("1790012345001"),
                        "NEXORF S.A.S.",
                        null,
                        "Quito",
                        Instant.parse(
                                "2026-10-05T15:00:00Z"
                        )
                );

        adapter.save(taxpayer);

        entityManager.flush();
        entityManager.clear();

        boolean exists =
                adapter.existsByTenantIdAndRuc(
                        tenantId,
                        new Ruc("1790012345001")
                );

        assertTrue(exists);
    }
}