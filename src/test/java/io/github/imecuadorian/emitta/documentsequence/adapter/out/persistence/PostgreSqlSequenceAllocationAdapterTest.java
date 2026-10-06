package io.github.imecuadorian.emitta.documentsequence.adapter.out.persistence;

import io.github.imecuadorian.emitta.documentsequence.domain.SequenceExhaustedException;
import io.github.imecuadorian.emitta.documentsequence.domain.SequenceScope;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class PostgreSqlSequenceAllocationAdapterTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("emitta_db")
                    .withUsername("emitta_test")
                    .withPassword("emitta_test");

    private static JdbcTemplate jdbcTemplate;

    private PostgreSqlSequenceAllocationAdapter adapter;

    private UUID pointOfIssueId;

    @BeforeAll
    static void migrateDatabase() {

        DataSource dataSource =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                );

        Flyway.configure()
                .dataSource(dataSource)
                .schemas("emitta")
                .defaultSchema("emitta")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate =
                new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {

        adapter =
                new PostgreSqlSequenceAllocationAdapter(
                        jdbcTemplate
                );

        jdbcTemplate.update(
                """
                TRUNCATE TABLE
                    emitta.document_sequences,
                    emitta.points_of_issue,
                    emitta.establishments,
                    emitta.taxpayers,
                    emitta.tenants
                CASCADE
                """
        );

        createFiscalHierarchy();
    }

    @Test
    void shouldAllocateFirstSequentialAsOne() {

        SequenceScope scope =
                productionInvoiceScope();

        SequentialNumber sequential =
                adapter.allocateNext(scope);

        assertEquals(
                1L,
                sequential.value()
        );

        assertEquals(
                "000000001",
                sequential.formatted()
        );
    }

    @Test
    void shouldAllocateConsecutiveSequentialNumbers() {

        SequenceScope scope =
                productionInvoiceScope();

        SequentialNumber first =
                adapter.allocateNext(scope);

        SequentialNumber second =
                adapter.allocateNext(scope);

        SequentialNumber third =
                adapter.allocateNext(scope);

        assertEquals(1L, first.value());
        assertEquals(2L, second.value());
        assertEquals(3L, third.value());
    }

    @Test
    void shouldMaintainIndependentSequencesByEnvironment() {

        SequenceScope testScope =
                new SequenceScope(
                        pointOfIssueId,
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST
                );

        SequenceScope productionScope =
                new SequenceScope(
                        pointOfIssueId,
                        DocumentType.INVOICE,
                        FiscalEnvironment.PRODUCTION
                );

        SequentialNumber testSequential =
                adapter.allocateNext(
                        testScope
                );

        SequentialNumber productionSequential =
                adapter.allocateNext(
                        productionScope
                );

        assertEquals(
                1L,
                testSequential.value()
        );

        assertEquals(
                1L,
                productionSequential.value()
        );
    }

    @Test
    void shouldAllocateUniqueNumbersUnderConcurrency()
            throws Exception {

        int allocationCount = 50;

        SequenceScope scope =
                productionInvoiceScope();

        CountDownLatch startGate =
                new CountDownLatch(1);

        try (
                ExecutorService executor =
                        Executors.newFixedThreadPool(10)
        ) {

            List<Future<Long>> futures =
                    new ArrayList<>();

            for (
                    int i = 0;
                    i < allocationCount;
                    i++
            ) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startGate.await();

                                    return adapter
                                            .allocateNext(scope)
                                            .value();
                                }
                        )
                );
            }

            startGate.countDown();

            List<Long> allocated =
                    new ArrayList<>();

            for (Future<Long> future : futures) {
                allocated.add(
                        future.get()
                );
            }

            assertEquals(
                    allocationCount,
                    new HashSet<>(allocated).size()
            );

            allocated.sort(Long::compareTo);

            for (
                    int i = 0;
                    i < allocationCount;
                    i++
            ) {
                assertEquals(
                        i + 1L,
                        allocated.get(i)
                );
            }
        }
    }

    @Test
    void shouldRejectAllocationWhenSequenceIsExhausted() {

        SequenceScope scope =
                productionInvoiceScope();

        adapter.allocateNext(scope);

        jdbcTemplate.update(
                """
                UPDATE emitta.document_sequences
                SET current_value = 999999999
                WHERE point_of_issue_id = ?
                  AND document_type = ?
                  AND environment = ?
                """,
                pointOfIssueId,
                DocumentType.INVOICE.name(),
                FiscalEnvironment.PRODUCTION.name()
        );

        assertThrows(
                SequenceExhaustedException.class,
                () -> adapter.allocateNext(scope)
        );
    }

    private SequenceScope productionInvoiceScope() {

        return new SequenceScope(
                pointOfIssueId,
                DocumentType.INVOICE,
                FiscalEnvironment.PRODUCTION
        );
    }

    private void createFiscalHierarchy() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

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
                "Sequence Test Tenant",
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
}