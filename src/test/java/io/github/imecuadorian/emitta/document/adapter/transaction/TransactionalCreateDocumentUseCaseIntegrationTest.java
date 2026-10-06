package io.github.imecuadorian.emitta.document.adapter.transaction;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class TransactionalCreateDocumentUseCaseIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollbackDatabaseChangesWhenDelegateFails() {

        UUID eventId =
                UUID.randomUUID();

        UUID aggregateId =
                UUID.randomUUID();

        CreateDocumentUseCase failingDelegate =
                command -> {

                    jdbcTemplate.update(
                            """
                            INSERT INTO emitta.outbox_events (
                                id,
                                tenant_id,
                                aggregate_type,
                                aggregate_id,
                                event_type,
                                payload
                            )
                            VALUES (
                                ?, NULL, ?, ?, ?, '{}'::jsonb
                            )
                            """,
                            eventId,
                            "DOCUMENT",
                            aggregateId,
                            "document.received.v1"
                    );

                    throw new IllegalStateException(
                            "Simulated failure"
                    );
                };

        TransactionalCreateDocumentUseCase useCase =
                new TransactionalCreateDocumentUseCase(
                        failingDelegate,
                        new TransactionTemplate(
                                transactionManager
                        )
                );

        CreateDocumentCommand command =
                new CreateDocumentCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        "rollback-test",
                        Instant.parse(
                                "2026-10-06T03:00:00Z"
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> useCase.create(
                        command
                )
        );

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM emitta.outbox_events
                        WHERE id = ?
                        """,
                        Integer.class,
                        eventId
                );

        assertEquals(
                0,
                count
        );
    }
}