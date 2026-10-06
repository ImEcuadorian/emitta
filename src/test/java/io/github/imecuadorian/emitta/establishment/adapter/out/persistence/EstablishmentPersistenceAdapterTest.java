package io.github.imecuadorian.emitta.establishment.adapter.out.persistence;

import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentStatus;
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
@Import(EstablishmentPersistenceAdapter.class)
@Testcontainers
class EstablishmentPersistenceAdapterTest {

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
    private EstablishmentPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID taxpayerId;

    @BeforeEach
    void createParentRecords() {

        UUID tenantId =
                UUID.randomUUID();

        taxpayerId =
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
                "Establishment Integration Tenant",
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
    }

    @Test
    void shouldSaveAndRestoreEstablishment() {

        UUID establishmentId =
                UUID.randomUUID();

        Instant createdAt =
                Instant.parse(
                        "2026-10-05T23:00:00Z"
                );

        Establishment establishment =
                Establishment.create(
                        establishmentId,
                        taxpayerId,
                        new EstablishmentCode("001"),
                        "Matriz Quito",
                        "Av. Principal 123",
                        createdAt
                );

        adapter.save(establishment);

        entityManager.flush();
        entityManager.clear();

        Optional<Establishment> result =
                adapter.findById(
                        establishmentId
                );

        assertTrue(result.isPresent());

        Establishment restored =
                result.orElseThrow();

        assertEquals(
                establishmentId,
                restored.getId()
        );

        assertEquals(
                taxpayerId,
                restored.getTaxpayerId()
        );

        assertEquals(
                "001",
                restored.getCode().value()
        );

        assertEquals(
                "Matriz Quito",
                restored.getName()
        );

        assertEquals(
                "Av. Principal 123",
                restored.getAddress()
        );

        assertEquals(
                EstablishmentStatus.ACTIVE,
                restored.getStatus()
        );

        assertEquals(
                createdAt,
                restored.getCreatedAt()
        );
    }

    @Test
    void shouldFindExistingCodeWithinTaxpayer() {

        Establishment establishment =
                Establishment.create(
                        UUID.randomUUID(),
                        taxpayerId,
                        new EstablishmentCode("001"),
                        "Matriz",
                        "Quito",
                        Instant.parse(
                                "2026-10-05T23:00:00Z"
                        )
                );

        adapter.save(establishment);

        entityManager.flush();
        entityManager.clear();

        boolean exists =
                adapter.existsByTaxpayerIdAndCode(
                        taxpayerId,
                        new EstablishmentCode("001")
                );

        assertTrue(exists);
    }
}