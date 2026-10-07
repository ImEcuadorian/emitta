package io.github.imecuadorian.emitta.fiscalprocessing.integration;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.adapter.config.ArtifactStorageProperties;
import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.support.JwtTestProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = {
                "emitta.outbox.publisher.enabled=false",
                "emitta.fiscal.worker.enabled=true",
                "emitta.artifacts.storage.enabled=true",
                "emitta.fiscal.provider-ruc=1799999999001",
                "spring.rabbitmq.publisher-confirm-type=correlated",
                "spring.datasource.hikari.schema=emitta"
        }
)
@Testcontainers
@Tag("object-storage-integration")
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class FiscalProcessingArtifactEndToEndIntegrationTest {

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
                    "2026-10-06T23:00:00Z"
            );

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        JwtTestProperties.register(
                registry
        );

        registry.add(
                "emitta.artifacts.storage.endpoint",
                () ->
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_ENDPOINT"
                        )
        );

        registry.add(
                "emitta.artifacts.storage.region",
                () ->
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_REGION"
                        )
        );

        registry.add(
                "emitta.artifacts.storage.bucket",
                () ->
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_BUCKET"
                        )
        );

        registry.add(
                "emitta.artifacts.storage.access-key",
                () ->
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_ACCESS_KEY"
                        )
        );

        registry.add(
                "emitta.artifacts.storage.secret-key",
                () ->
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_SECRET_KEY"
                        )
        );

        registry.add(
                "emitta.artifacts.storage.path-style-access",
                () ->
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_PATH_STYLE"
                        )
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CreateDocumentUseCase createDocumentUseCase;

    @Autowired
    private PublishPendingOutboxUseCase
            publishPendingOutboxUseCase;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ArtifactStorageProperties storageProperties;

    private UUID tenantId;
    private UUID taxpayerId;
    private UUID establishmentId;
    private UUID pointOfIssueId;

    @BeforeEach
    void setUp() {

        ensureBucketExists();

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
                "Fiscal Artifact E2E Tenant",
                "ACTIVE"
        );

        jdbcTemplate.update(
                """
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taxpayerId,
                tenantId,
                "1790012345001",
                "EMITTA TEST S.A.S.",
                "EMITTA TEST",
                "Quito, Ecuador",
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
                "Quito, Ecuador",
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
    void shouldProcessInvoiceAndPersistUnsignedXmlEndToEnd()
            throws Exception {

        /*
         * 1. Create the fiscal document and Outbox event.
         */
        CreateDocumentResult created =
                createDocumentUseCase.create(
                        new CreateDocumentCommand(
                                tenantId,
                                pointOfIssueId,
                                DocumentType.INVOICE,
                                FiscalEnvironment.TEST,
                                "artifact-e2e-"
                                        + UUID.randomUUID(),
                                ISSUED_AT
                        )
                );

        assertTrue(
                created.created()
        );

        assertEquals(
                DocumentStatus.QUEUED,
                created.document()
                        .getStatus()
        );

        UUID documentId =
                created.document()
                        .getId();

        /*
         * 2. Persist the invoice data required by the XML source
         * adapter before the asynchronous worker receives the event.
         */
        seedInvoice(
                documentId
        );

        /*
         * 3. Publish the pending Outbox event to RabbitMQ.
         */
        int published =
                publishPendingOutboxUseCase
                        .publishBatch();

        assertEquals(
                1,
                published
        );

        /*
         * 4. Wait for both fiscal identity generation and artifact
         * persistence. Waiting only for GENERATING is insufficient
         * because object storage happens after the DB transaction.
         */
        Map<String, Object> document =
                waitUntilArtifactStored(
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
                        "sequential"
                )
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
         * 5. PostgreSQL must contain the artifact metadata.
         */
        Map<String, Object> artifact =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            artifact_type,
                            content_type,
                            storage_key,
                            sha256,
                            size_bytes
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'UNSIGNED_XML'
                        """,
                        documentId
                );

        assertEquals(
                "UNSIGNED_XML",
                artifact.get(
                        "artifact_type"
                )
        );

        assertEquals(
                "application/xml",
                artifact.get(
                        "content_type"
                )
        );

        String expectedStorageKey =
                "documents/"
                        + documentId
                        + "/unsigned.xml";

        assertEquals(
                expectedStorageKey,
                artifact.get(
                        "storage_key"
                )
        );

        String persistedSha256 =
                (String) artifact.get(
                        "sha256"
                );

        /*
         * 6. The exact object must exist in MinIO.
         */
        ResponseBytes<GetObjectResponse> object =
                s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(
                                        storageProperties.bucket()
                                )
                                .key(
                                        expectedStorageKey
                                )
                                .build()
                );

        byte[] xmlBytes =
                object.asByteArray();

        assertTrue(
                xmlBytes.length > 0
        );

        assertEquals(
                ((Number) artifact.get(
                        "size_bytes"
                )).longValue(),
                xmlBytes.length
        );

        assertEquals(
                persistedSha256,
                sha256(
                        xmlBytes
                )
        );

        assertEquals(
                "application/xml",
                object.response()
                        .contentType()
        );

        assertEquals(
                persistedSha256,
                object.response()
                        .metadata()
                        .get(
                                "sha256"
                        )
        );

        assertEquals(
                documentId.toString(),
                object.response()
                        .metadata()
                        .get(
                                "document-id"
                        )
        );

        assertEquals(
                "UNSIGNED_XML",
                object.response()
                        .metadata()
                        .get(
                                "artifact-type"
                        )
        );

        /*
         * 7. Verify that the stored object is the actual invoice XML
         * produced by Emitta, not an arbitrary object.
         */
        String xml =
                new String(
                        xmlBytes,
                        StandardCharsets.UTF_8
                );

        assertTrue(
                xml.contains(
                        "<factura"
                )
        );

        assertTrue(
                xml.contains(
                        "version=\"2.1.0\""
                )
        );

        assertTrue(
                xml.contains(
                        accessKey
                )
        );

        assertTrue(
                xml.contains(
                        "<razonSocial>EMITTA TEST S.A.S.</razonSocial>"
                )
        );

        assertTrue(
                xml.contains(
                        "<razonSocialComprador>CONSUMIDOR FINAL</razonSocialComprador>"
                )
        );

        assertTrue(
                xml.contains(
                        "<importeTotal>20.70</importeTotal>"
                )
        );

        assertTrue(
                xml.contains(
                        "<campoAdicional nombre=\"RUC Proveedor\">"
                                + "1799999999001"
                                + "</campoAdicional>"
                )
        );
    }

    private void seedInvoice(
            UUID documentId
    ) {

        UUID itemId =
                UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO emitta.invoices (
                    document_id,
                    buyer_identification_type,
                    buyer_identification,
                    buyer_name,
                    subtotal,
                    discount_total,
                    tax_total,
                    total,
                    currency
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                documentId,
                "07",
                "9999999999999",
                "CONSUMIDOR FINAL",
                18.00,
                2.00,
                2.70,
                20.70,
                "DOLAR"
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.invoice_items (
                    id,
                    invoice_id,
                    line_number,
                    sku,
                    description,
                    quantity,
                    unit_price,
                    discount,
                    subtotal,
                    tax_total,
                    total
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                itemId,
                documentId,
                1,
                "P001",
                "Producto E2E",
                2.000000,
                10.000000,
                2.00,
                18.00,
                2.70,
                20.70
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.invoice_item_taxes (
                    invoice_item_id,
                    tax_code,
                    percentage_code,
                    rate,
                    taxable_base,
                    tax_amount
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                itemId,
                "2",
                "4",
                15.0000,
                18.00,
                2.70
        );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.invoice_payments (
                    invoice_id,
                    line_number,
                    payment_method,
                    total
                )
                VALUES (?, ?, ?, ?)
                """,
                documentId,
                1,
                "01",
                20.70
        );
    }

    private Map<String, Object> waitUntilArtifactStored(
            UUID documentId
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + 15_000_000_000L;

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

            Integer artifactCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM emitta.document_artifacts
                            WHERE document_id = ?
                              AND artifact_type = 'UNSIGNED_XML'
                            """,
                            Integer.class,
                            documentId
                    );

            boolean processed =
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
                    ) != null
                            && artifactCount != null
                            && artifactCount == 1;

            if (processed) {
                return lastState;
            }

            Thread.sleep(
                    100
            );
        }

        fail(
                "Fiscal document artifact was not stored within 15 seconds. Last state: "
                        + lastState
        );

        throw new IllegalStateException(
                "Unreachable"
        );
    }

    private void ensureBucketExists() {

        try {

            s3Client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(
                                    storageProperties.bucket()
                            )
                            .build()
            );

        } catch (S3Exception exception) {

            if (exception.statusCode() != 404) {
                throw exception;
            }

            s3Client.createBucket(
                    CreateBucketRequest.builder()
                            .bucket(
                                    storageProperties.bucket()
                            )
                            .build()
            );
        }
    }

    private static String sha256(
            byte[] content
    ) throws Exception {

        return HexFormat.of()
                .formatHex(
                        MessageDigest
                                .getInstance(
                                        "SHA-256"
                                )
                                .digest(
                                        content
                                )
                );
    }

    private static String requiredEnvironmentVariable(
            String name
    ) {

        String value =
                System.getenv(
                        name
                );

        if (
                value == null
                        || value.isBlank()
        ) {

            throw new IllegalStateException(
                    "Required environment variable is missing: "
                            + name
            );
        }

        return value;
    }
}