package io.github.imecuadorian.emitta.invoice.integration;

import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.invoice.adapter.transaction.TransactionalCreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceBuyerCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceItemCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoicePaymentCommand;
import io.github.imecuadorian.emitta.invoice.application.command.InvoiceTaxCommand;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;
import io.github.imecuadorian.emitta.invoice.application.port.in.CreateInvoiceUseCase;
import io.github.imecuadorian.emitta.invoice.application.port.out.InvoiceRepository;
import io.github.imecuadorian.emitta.invoice.application.service.CreateInvoiceService;
import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.support.JwtTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = {
                "emitta.outbox.publisher.enabled=false",
                "emitta.fiscal.worker.enabled=true",
                "spring.rabbitmq.publisher-confirm-type=correlated"
        }
)
@Testcontainers
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class CreateInvoiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    "postgres:18"
            )
                    .withDatabaseName(
                            "emitta_db"
                    )
                    .withUsername(
                            "emitta_test"
                    )
                    .withPassword(
                            "emitta_test"
                    );

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(
                    "rabbitmq:4-management-alpine"
            );

    private static final Instant ISSUED_AT =
            Instant.parse(
                    "2026-10-05T20:00:00Z"
            );

    @DynamicPropertySource
    static void jwtProperties(
            DynamicPropertyRegistry registry
    ) {
        JwtTestProperties.register(
                registry
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CreateInvoiceUseCase
            createInvoiceUseCase;

    @Autowired
    private CreateDocumentUseCase
            createDocumentUseCase;

    @Autowired
    private InvoiceRepository
            invoiceRepository;

    @Autowired
    private PublishPendingOutboxUseCase
            publishPendingOutboxUseCase;

    @Autowired
    private PlatformTransactionManager
            transactionManager;

    private UUID tenantId;
    private UUID taxpayerId;
    private UUID establishmentId;
    private UUID pointOfIssueId;

    @BeforeEach
    void setUpFiscalHierarchy() {

        tenantId =
                UUID.randomUUID();

        taxpayerId =
                UUID.randomUUID();

        establishmentId =
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
                "Invoice Integration Tenant",
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
                "EMITTA INTEGRATION S.A.S.",
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
    void shouldRollbackCompleteInvoiceCreationWhenPersistenceFails() {

        UUID itemId =
                UUID.randomUUID();

        UUID taxId =
                UUID.randomUUID();

        UUID paymentId =
                UUID.randomUUID();

        /*
         * IDs requested by CreateInvoiceService:
         *
         * 1 -> item
         * 2 -> item tax
         * 3 -> payment 1
         * 4 -> payment 2
         *
         * Payment 2 deliberately receives the same primary key
         * as payment 1, causing PostgreSQL to reject the insert
         * after the previous business rows were already written.
         */
        List<UUID> generatedIds =
                List.of(
                        itemId,
                        taxId,
                        paymentId,
                        paymentId
                );

        AtomicInteger index =
                new AtomicInteger();

        Supplier<UUID> collidingGenerator =
                () ->
                        generatedIds.get(
                                index.getAndIncrement()
                        );

        CreateInvoiceService service =
                new CreateInvoiceService(
                        createDocumentUseCase,
                        invoiceRepository,
                        collidingGenerator
                );

        CreateInvoiceUseCase transactional =
                new TransactionalCreateInvoiceUseCase(
                        service,
                        new TransactionTemplate(
                                transactionManager
                        )
                );

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        transactional.create(
                                rollbackCommand()
                        )
        );

        Integer documents =
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.documents
                        WHERE tenant_id = ?
                          AND idempotency_key = ?
                        """,
                        tenantId,
                        "rollback-invoice-001"
                );

        Integer invoices =
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoices
                        WHERE buyer_name = ?
                        """,
                        "ROLLBACK BUYER"
                );

        Integer items =
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoice_items
                        WHERE id = ?
                        """,
                        itemId
                );

        Integer taxes =
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoice_item_taxes
                        WHERE id = ?
                        """,
                        taxId
                );

        Integer payments =
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoice_payments
                        WHERE id = ?
                        """,
                        paymentId
                );

        Integer outbox =
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.outbox_events
                        WHERE tenant_id = ?
                        """,
                        tenantId
                );

        assertEquals(
                0,
                documents
        );

        assertEquals(
                0,
                invoices
        );

        assertEquals(
                0,
                items
        );

        assertEquals(
                0,
                taxes
        );

        assertEquals(
                0,
                payments
        );

        assertEquals(
                0,
                outbox
        );
    }

    @Test
    void shouldCreateAndProcessCompleteInvoiceEndToEnd()
            throws InterruptedException {

        /*
         * 1. Full invoice creation.
         */
        CreateInvoiceResult result =
                createInvoiceUseCase.create(
                        successCommand()
                );

        assertTrue(
                result.created()
        );

        assertEquals(
                DocumentStatus.QUEUED,
                result.document()
                        .getStatus()
        );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                result.invoice()
                        .getSubtotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.00"
                ),
                result.invoice()
                        .getDiscountTotal()
        );

        assertEquals(
                new BigDecimal(
                        "2.70"
                ),
                result.invoice()
                        .getTaxTotal()
        );

        assertEquals(
                new BigDecimal(
                        "20.70"
                ),
                result.invoice()
                        .getTotal()
        );

        UUID documentId =
                result.document()
                        .getId();

        /*
         * 2. Verify the complete invoice snapshot was committed.
         */
        Map<String, Object> invoice =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            subtotal,
                            discount_total,
                            tax_total,
                            total,
                            currency
                        FROM emitta.invoices
                        WHERE document_id = ?
                        """,
                        documentId
                );

        assertEquals(
                new BigDecimal(
                        "18.00"
                ),
                invoice.get(
                        "subtotal"
                )
        );

        assertEquals(
                new BigDecimal(
                        "2.00"
                ),
                invoice.get(
                        "discount_total"
                )
        );

        assertEquals(
                new BigDecimal(
                        "2.70"
                ),
                invoice.get(
                        "tax_total"
                )
        );

        assertEquals(
                new BigDecimal(
                        "20.70"
                ),
                invoice.get(
                        "total"
                )
        );

        assertEquals(
                "DOLAR",
                invoice.get(
                        "currency"
                )
        );

        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoice_items
                        WHERE invoice_id = ?
                        """,
                        documentId
                )
        );

        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoice_item_taxes tax
                        JOIN emitta.invoice_items item
                          ON item.id = tax.invoice_item_id
                        WHERE item.invoice_id = ?
                        """,
                        documentId
                )
        );

        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.invoice_payments
                        WHERE invoice_id = ?
                        """,
                        documentId
                )
        );

        /*
         * 3. Transactional Outbox must exist but must not yet
         * be published because the scheduler is disabled.
         */
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*)
                        FROM emitta.outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'document.received.v1'
                          AND published_at IS NULL
                        """,
                        documentId
                )
        );

        /*
         * 4. Publish through the real Outbox publisher.
         */
        int published =
                publishPendingOutboxUseCase
                        .publishBatch();

        assertEquals(
                1,
                published
        );

        /*
         * 5. RabbitMQ listener asynchronously starts fiscal
         * processing.
         */
        Map<String, Object> document =
                waitUntilFiscalIdentityExists(
                        documentId
                );

        assertEquals(
                "GENERATING",
                document.get(
                        "status"
                )
        );

        assertNotNull(
                document.get(
                        "processing_started_at"
                )
        );

        assertEquals(
                1L,
                ((Number) document.get(
                        "sequential"
                )).longValue()
        );

        String accessKey =
                (String) document.get(
                        "access_key"
                );

        assertNotNull(
                accessKey
        );

        assertTrue(
                accessKey.matches(
                        "\\d{49}"
                )
        );

        /*
         * 6. Sequence storage agrees with the Document.
         */
        Long sequence =
                jdbcTemplate.queryForObject(
                        """
                        SELECT current_value
                        FROM emitta.document_sequences
                        WHERE point_of_issue_id = ?
                          AND document_type = 'INVOICE'
                          AND environment = 'TEST'
                        """,
                        Long.class,
                        pointOfIssueId
                );

        assertEquals(
                1L,
                sequence
        );

        /*
         * 7. Outbox event is now acknowledged.
         */
        Map<String, Object> outbox =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            published_at,
                            attempts,
                            claimed_at,
                            claimed_by,
                            last_error
                        FROM emitta.outbox_events
                        WHERE aggregate_id = ?
                          AND event_type = 'document.received.v1'
                        """,
                        documentId
                );

        assertNotNull(
                outbox.get(
                        "published_at"
                )
        );

        assertEquals(
                1,
                ((Number) outbox.get(
                        "attempts"
                )).intValue()
        );

        assertNull(
                outbox.get(
                        "claimed_at"
                )
        );

        assertNull(
                outbox.get(
                        "claimed_by"
                )
        );

        assertNull(
                outbox.get(
                        "last_error"
                )
        );
    }

    private CreateInvoiceCommand successCommand() {

        return new CreateInvoiceCommand(
                tenantId,
                pointOfIssueId,
                FiscalEnvironment.TEST,
                "success-invoice-001",
                ISSUED_AT,
                null,
                new InvoiceBuyerCommand(
                        "07",
                        "9999999999999",
                        "CONSUMIDOR FINAL",
                        null,
                        null
                ),
                List.of(
                        new InvoiceItemCommand(
                                "P001",
                                "Integration product",
                                new BigDecimal(
                                        "2"
                                ),
                                new BigDecimal(
                                        "10.00"
                                ),
                                new BigDecimal(
                                        "2.00"
                                ),
                                List.of(
                                        new InvoiceTaxCommand(
                                                "2",
                                                "4",
                                                new BigDecimal(
                                                        "15"
                                                )
                                        )
                                )
                        )
                ),
                List.of(
                        new InvoicePaymentCommand(
                                "01",
                                new BigDecimal(
                                        "20.70"
                                ),
                                null,
                                null
                        )
                ),
                new BigDecimal(
                        "20.70"
                )
        );
    }

    private CreateInvoiceCommand rollbackCommand() {

        return new CreateInvoiceCommand(
                tenantId,
                pointOfIssueId,
                FiscalEnvironment.TEST,
                "rollback-invoice-001",
                ISSUED_AT,
                null,
                new InvoiceBuyerCommand(
                        "04",
                        "1799999999001",
                        "ROLLBACK BUYER",
                        "rollback@example.com",
                        "Quito"
                ),
                List.of(
                        new InvoiceItemCommand(
                                "ROLLBACK-P001",
                                "Rollback integration product",
                                BigDecimal.ONE,
                                new BigDecimal(
                                        "10.00"
                                ),
                                BigDecimal.ZERO,
                                List.of(
                                        new InvoiceTaxCommand(
                                                "2",
                                                "4",
                                                new BigDecimal(
                                                        "15"
                                                )
                                        )
                                )
                        )
                ),
                List.of(
                        new InvoicePaymentCommand(
                                "01",
                                new BigDecimal(
                                        "5.00"
                                ),
                                null,
                                null
                        ),
                        new InvoicePaymentCommand(
                                "19",
                                new BigDecimal(
                                        "6.50"
                                ),
                                null,
                                null
                        )
                ),
                new BigDecimal(
                        "11.50"
                )
        );
    }

    private Map<String, Object>
    waitUntilFiscalIdentityExists(
            UUID documentId
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + 10_000_000_000L;

        Map<String, Object> lastState =
                null;

        while (
                System.nanoTime()
                        < deadline
        ) {

            lastState =
                    jdbcTemplate.queryForMap(
                            """
                            SELECT
                                status,
                                sequential,
                                access_key,
                                processing_started_at
                            FROM emitta.documents
                            WHERE id = ?
                            """,
                            documentId
                    );

            boolean ready =
                    "GENERATING".equals(
                            lastState.get(
                                    "status"
                            )
                    )
                            && lastState.get(
                            "sequential"
                    ) != null
                            && lastState.get(
                            "access_key"
                    ) != null;

            if (ready) {
                return lastState;
            }

            Thread.sleep(
                    100
            );
        }

        fail(
                "Invoice was not fiscally processed within 10 seconds. Last state: "
                        + lastState
        );

        throw new IllegalStateException(
                "Unreachable"
        );
    }

    private Integer count(
            String sql,
            Object... parameters
    ) {

        return jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                parameters
        );
    }
}