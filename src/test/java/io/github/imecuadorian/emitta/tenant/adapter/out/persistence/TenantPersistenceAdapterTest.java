package io.github.imecuadorian.emitta.tenant.adapter.out.persistence;

import io.github.imecuadorian.emitta.tenant.domain.Tenant;
import io.github.imecuadorian.emitta.tenant.domain.TenantStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
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
@Import(TenantPersistenceAdapter.class)
@Testcontainers
class TenantPersistenceAdapterTest {

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
    private TenantPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndRestoreTenant() {

        UUID id = UUID.randomUUID();

        Instant createdAt =
                Instant.parse("2026-10-04T20:00:00Z");

        Tenant tenant = Tenant.create(
                id,
                "NEXORF",
                createdAt
        );

        adapter.save(tenant);

        /*
         * Force Hibernate to send the INSERT to PostgreSQL,
         * then clear the persistence context.
         *
         * This prevents findById() from simply returning an
         * already cached entity and proves that PostgreSQL
         * persistence actually works.
         */
        entityManager.flush();
        entityManager.clear();

        Optional<Tenant> result =
                adapter.findById(id);

        assertTrue(result.isPresent());

        Tenant restored = result.orElseThrow();

        assertEquals(
                id,
                restored.getId()
        );

        assertEquals(
                "NEXORF",
                restored.getName()
        );

        assertEquals(
                TenantStatus.ACTIVE,
                restored.getStatus()
        );

        assertEquals(
                createdAt,
                restored.getCreatedAt()
        );

        assertEquals(
                createdAt,
                restored.getUpdatedAt()
        );
    }

    @Test
    void shouldReportExistingTenant() {

        UUID id = UUID.randomUUID();

        Tenant tenant = Tenant.create(
                id,
                "Emitta Test Tenant",
                Instant.parse("2026-10-04T21:00:00Z")
        );

        adapter.save(tenant);

        entityManager.flush();
        entityManager.clear();

        assertTrue(
                adapter.existsById(id)
        );
    }
}