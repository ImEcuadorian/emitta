package io.github.imecuadorian.emitta.fiscalprocessing.integration;

import io.github.imecuadorian.emitta.document.application.command.CreateDocumentCommand;
import io.github.imecuadorian.emitta.document.application.model.CreateDocumentResult;
import io.github.imecuadorian.emitta.document.application.port.in.CreateDocumentUseCase;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.adapter.config.ArtifactStorageProperties;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
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

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.AesGcmSecretCipher;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.CertificateSecretAssociatedData;

import org.junit.jupiter.api.io.TempDir;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

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
                "emitta.signing.enabled=true",
                "spring.datasource.hikari.schema=emitta"
        }
)
@Testcontainers
@Tag("object-storage-integration")
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class FiscalProcessingArtifactEndToEndIntegrationTest {

    private static final String CERTIFICATE_ALIAS =
            "emitta-test";

    private static final String CERTIFICATE_PASSWORD =
            "emitta-test-password";

    private static final String TEST_CERTIFICATE_MASTER_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            new byte[32]
                    );

    private static final String XMLDSIG_NAMESPACE =
            "http://www.w3.org/2000/09/xmldsig#";

    private static final String XADES_NAMESPACE =
            "http://uri.etsi.org/01903/v1.3.2#";

    @TempDir
    Path tempDirectory;

    private X509Certificate signingCertificate;

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
                "EMITTA_CERTIFICATE_MASTER_KEY_B64",
                () ->
                        TEST_CERTIFICATE_MASTER_KEY
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

    @Autowired
    private ProcessFiscalDocumentUseCase
            processFiscalDocumentUseCase;

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

        try {

            seedSigningCertificate();

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to prepare signing certificate",
                    exception
            );
        }
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
                waitUntilSigned(
                        documentId
                );

        assertEquals(
                "SIGNED",
                document.get(
                        "status"
                )
        );

        assertNotNull(
                document.get(
                        "signed_at"
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
         * 5. PostgreSQL must contain the unsigned artifact metadata.
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
         * 6. The exact unsigned XML must exist in MinIO.
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
         * 7. PostgreSQL and MinIO must contain the signed XML.
         */
        Map<String, Object> signedArtifact =
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
                          AND artifact_type = 'SIGNED_XML'
                        """,
                        documentId
                );

        assertEquals(
                "SIGNED_XML",
                signedArtifact.get(
                        "artifact_type"
                )
        );

        assertEquals(
                "application/xml",
                signedArtifact.get(
                        "content_type"
                )
        );

        String signedStorageKey =
                "documents/"
                        + documentId
                        + "/signed.xml";

        assertEquals(
                signedStorageKey,
                signedArtifact.get(
                        "storage_key"
                )
        );

        ResponseBytes<GetObjectResponse> signedObject =
                s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(
                                        storageProperties.bucket()
                                )
                                .key(
                                        signedStorageKey
                                )
                                .build()
                );

        byte[] signedXml =
                signedObject.asByteArray();

        assertTrue(
                signedXml.length
                        > xmlBytes.length
        );

        assertEquals(
                ((Number) signedArtifact.get(
                        "size_bytes"
                )).longValue(),
                signedXml.length
        );

        assertEquals(
                signedArtifact.get(
                        "sha256"
                ),
                sha256(
                        signedXml
                )
        );

        assertEquals(
                "application/xml",
                signedObject.response()
                        .contentType()
        );

        assertEquals(
                documentId.toString(),
                signedObject.response()
                        .metadata()
                        .get(
                                "document-id"
                        )
        );

        assertEquals(
                "SIGNED_XML",
                signedObject.response()
                        .metadata()
                        .get(
                                "artifact-type"
                        )
        );

        /*
         * 8. Signed XML must contain XAdES and be
         * cryptographically valid.
         */
        Document signedDocument =
                parseXml(
                        signedXml
                );

        NodeList signatures =
                signedDocument
                        .getElementsByTagNameNS(
                                XMLDSIG_NAMESPACE,
                                "Signature"
                        );

        assertEquals(
                1,
                signatures.getLength()
        );

        NodeList signedProperties =
                signedDocument
                        .getElementsByTagNameNS(
                                XADES_NAMESPACE,
                                "SignedProperties"
                        );

        assertEquals(
                1,
                signedProperties.getLength()
        );

        registerIdAttributes(
                signedDocument.getDocumentElement()
        );

        validateCryptographically(
                signedDocument,
                signingCertificate
        );
    }

    @Test
    void shouldReuseSignedXmlWhenRecoveringGeneratingDocument()
            throws Exception {

        /*
         * 1. Create and process a normal invoice all the way to SIGNED.
         */
        CreateDocumentResult created =
                createDocumentUseCase.create(
                        new CreateDocumentCommand(
                                tenantId,
                                pointOfIssueId,
                                DocumentType.INVOICE,
                                FiscalEnvironment.TEST,
                                "recovery-e2e-"
                                        + UUID.randomUUID(),
                                ISSUED_AT
                        )
                );

        assertTrue(
                created.created()
        );

        UUID documentId =
                created.document()
                        .getId();

        seedInvoice(
                documentId
        );

        int published =
                publishPendingOutboxUseCase
                        .publishBatch();

        assertEquals(
                1,
                published
        );

        waitUntilSigned(
                documentId
        );

        /*
         * 2. Capture the original SIGNED_XML metadata and bytes.
         */
        Map<String, Object> originalArtifact =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            id,
                            storage_key,
                            sha256,
                            size_bytes,
                            created_at
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'SIGNED_XML'
                        """,
                        documentId
                );

        String storageKey =
                (String) originalArtifact.get(
                        "storage_key"
                );

        byte[] originalSignedXml =
                s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(
                                        storageProperties.bucket()
                                )
                                .key(
                                        storageKey
                                )
                                .build()
                ).asByteArray();

        assertTrue(
                originalSignedXml.length > 0
        );

        /*
         * 3. Simulate a crash boundary:
         *
         * SIGNED_XML already exists durably, but PostgreSQL still
         * represents the document as GENERATING.
         */
        int affected =
                jdbcTemplate.update(
                        """
                        UPDATE emitta.documents
    
                        SET
                            status = 'GENERATING',
                            signed_at = NULL,
                            updated_at = CURRENT_TIMESTAMP
    
                        WHERE id = ?
                        """,
                        documentId
                );

        assertEquals(
                1,
                affected
        );

        /*
         * 4. Simulate RabbitMQ redelivery at the same application
         * entry point used by the consumer.
         *
         * Because the document is already GENERATING, the base fiscal
         * processor must treat this as recovery, not a new processing run.
         */
        ProcessFiscalDocumentResult recovered =
                processFiscalDocumentUseCase.process(
                        new ProcessFiscalDocumentCommand(
                                documentId
                        )
                );

        assertEquals(
                DocumentStatus.SIGNED,
                recovered.status()
        );

        assertFalse(
                recovered.processed()
        );

        /*
         * 5. Document must have been finalized again.
         */
        Map<String, Object> recoveredDocument =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            status,
                            signed_at
                        FROM emitta.documents
                        WHERE id = ?
                        """,
                        documentId
                );

        assertEquals(
                "SIGNED",
                recoveredDocument.get(
                        "status"
                )
        );

        assertNotNull(
                recoveredDocument.get(
                        "signed_at"
                )
        );

        /*
         * 6. There must still be exactly one SIGNED_XML artifact.
         */
        Integer signedArtifactCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'SIGNED_XML'
                        """,
                        Integer.class,
                        documentId
                );

        assertEquals(
                1,
                signedArtifactCount
        );

        /*
         * 7. Artifact metadata must remain exactly the same.
         */
        Map<String, Object> recoveredArtifact =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            id,
                            storage_key,
                            sha256,
                            size_bytes,
                            created_at
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'SIGNED_XML'
                        """,
                        documentId
                );

        assertEquals(
                originalArtifact.get(
                        "id"
                ),
                recoveredArtifact.get(
                        "id"
                )
        );

        assertEquals(
                originalArtifact.get(
                        "storage_key"
                ),
                recoveredArtifact.get(
                        "storage_key"
                )
        );

        assertEquals(
                originalArtifact.get(
                        "sha256"
                ),
                recoveredArtifact.get(
                        "sha256"
                )
        );

        assertEquals(
                originalArtifact.get(
                        "size_bytes"
                ),
                recoveredArtifact.get(
                        "size_bytes"
                )
        );

        assertEquals(
                originalArtifact.get(
                        "created_at"
                ),
                recoveredArtifact.get(
                        "created_at"
                )
        );

        /*
         * 8. The exact signed XML bytes must also be unchanged.
         */
        byte[] recoveredSignedXml =
                s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(
                                        storageProperties.bucket()
                                )
                                .key(
                                        storageKey
                                )
                                .build()
                ).asByteArray();

        assertArrayEquals(
                originalSignedXml,
                recoveredSignedXml
        );

        /*
         * 9. The recovered object must remain cryptographically valid.
         */
        Document recoveredSignedDocument =
                parseXml(
                        recoveredSignedXml
                );

        registerIdAttributes(
                recoveredSignedDocument
                        .getDocumentElement()
        );

        validateCryptographically(
                recoveredSignedDocument,
                signingCertificate
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

    private Map<String, Object> waitUntilSigned(
            UUID documentId
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + 20_000_000_000L;

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
                                processing_started_at,
                                signed_at
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
                              AND artifact_type IN (
                                  'UNSIGNED_XML',
                                  'SIGNED_XML'
                              )
                            """,
                            Integer.class,
                            documentId
                    );

            if (
                    "SIGNED".equals(
                            lastState.get(
                                    "status"
                            )
                    )
                            && artifactCount != null
                            && artifactCount == 2
            ) {

                return lastState;
            }

            Thread.sleep(
                    100
            );
        }

        fail(
                "Fiscal signing pipeline did not complete. Last state: "
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

    private void seedSigningCertificate()
            throws Exception {

        UUID certificateId =
                UUID.randomUUID();

        Path pkcs12Path =
                generatePkcs12();

        byte[] pkcs12 =
                Files.readAllBytes(
                        pkcs12Path
                );

        byte[] password =
                CERTIFICATE_PASSWORD
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        signingCertificate =
                loadCertificate(
                        pkcs12Path
                );

        AesGcmSecretCipher secretCipher =
                new AesGcmSecretCipher(
                        TEST_CERTIFICATE_MASTER_KEY
                );

        byte[] encryptedContent =
                secretCipher.encrypt(
                        pkcs12,
                        CertificateSecretAssociatedData.content(
                                taxpayerId,
                                certificateId
                        )
                );

        byte[] encryptedPassword =
                secretCipher.encrypt(
                        password,
                        CertificateSecretAssociatedData.password(
                                taxpayerId,
                                certificateId
                        )
                );

        jdbcTemplate.update(
                """
                INSERT INTO emitta.certificates (
                    id,
                    taxpayer_id,
                    alias,
                    fingerprint,
                    subject,
                    issuer,
                    valid_from,
                    valid_until,
                    status,
                    encrypted_content,
                    encrypted_password
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                certificateId,
                taxpayerId,
                CERTIFICATE_ALIAS,
                sha256(
                        signingCertificate.getEncoded()
                ),
                signingCertificate
                        .getSubjectX500Principal()
                        .getName(),
                signingCertificate
                        .getIssuerX500Principal()
                        .getName(),
                Timestamp.from(
                        signingCertificate
                                .getNotBefore()
                                .toInstant()
                ),
                Timestamp.from(
                        signingCertificate
                                .getNotAfter()
                                .toInstant()
                ),
                "ACTIVE",
                encryptedContent,
                encryptedPassword
        );

        jdbcTemplate.update(
                """
                UPDATE emitta.taxpayers
    
                SET active_signing_certificate_id = ?
    
                WHERE id = ?
                """,
                certificateId,
                taxpayerId
        );
    }

    private Path generatePkcs12()
            throws Exception {

        Path pkcs12Path =
                tempDirectory.resolve(
                        "emitta-e2e-test.p12"
                );

        Path keytool =
                resolveKeytool();

        Process process =
                new ProcessBuilder(
                        keytool.toString(),
                        "-genkeypair",
                        "-alias",
                        CERTIFICATE_ALIAS,
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-sigalg",
                        "SHA256withRSA",
                        "-dname",
                        "CN=Emitta E2E Test, OU=QA, O=Emitta, L=Quito, C=EC",
                        "-validity",
                        "3650",
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        pkcs12Path.toString(),
                        "-storepass",
                        CERTIFICATE_PASSWORD,
                        "-keypass",
                        CERTIFICATE_PASSWORD,
                        "-noprompt"
                )
                        .redirectErrorStream(
                                true
                        )
                        .start();

        String output;

        try (
                InputStream input =
                        process.getInputStream()
        ) {

            output =
                    new String(
                            input.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
        }

        int exitCode =
                process.waitFor();

        assertEquals(
                0,
                exitCode,
                () ->
                        "keytool failed:\n"
                                + output
        );

        assertTrue(
                Files.exists(
                        pkcs12Path
                )
        );

        return pkcs12Path;
    }

    private X509Certificate loadCertificate(
            Path pkcs12Path
    ) throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(
                        "PKCS12"
                );

        try (
                InputStream input =
                        Files.newInputStream(
                                pkcs12Path
                        )
        ) {

            keyStore.load(
                    input,
                    CERTIFICATE_PASSWORD
                            .toCharArray()
            );
        }

        return (X509Certificate)
                keyStore.getCertificate(
                        CERTIFICATE_ALIAS
                );
    }

    private static Path resolveKeytool() {

        boolean windows =
                System.getProperty(
                                "os.name"
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .contains(
                                "win"
                        );

        String executable =
                windows
                        ? "keytool.exe"
                        : "keytool";

        Path keytool =
                Path.of(
                        System.getProperty(
                                "java.home"
                        ),
                        "bin",
                        executable
                );

        if (
                !Files.isRegularFile(
                        keytool
                )
        ) {

            throw new IllegalStateException(
                    "keytool was not found at: "
                            + keytool
            );
        }

        return keytool;
    }

    private static Document parseXml(
            byte[] xml
    ) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(
                true
        );

        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        return factory
                .newDocumentBuilder()
                .parse(
                        new ByteArrayInputStream(
                                xml
                        )
                );
    }

    private static void registerIdAttributes(
            Element element
    ) {

        registerIdAttribute(
                element,
                "Id"
        );

        registerIdAttribute(
                element,
                "ID"
        );

        registerIdAttribute(
                element,
                "id"
        );

        NodeList children =
                element.getChildNodes();

        for (
                int index = 0;
                index < children.getLength();
                index++
        ) {

            Node child =
                    children.item(
                            index
                    );

            if (
                    child instanceof Element childElement
            ) {

                registerIdAttributes(
                        childElement
                );
            }
        }
    }

    private static void registerIdAttribute(
            Element element,
            String attributeName
    ) {

        if (
                element.hasAttribute(
                        attributeName
                )
        ) {

            element.setIdAttribute(
                    attributeName,
                    true
            );
        }
    }

    private static void validateCryptographically(
            Document document,
            X509Certificate certificate
    ) throws Exception {

        Node signatureNode =
                document
                        .getElementsByTagNameNS(
                                XMLDSIG_NAMESPACE,
                                "Signature"
                        )
                        .item(0);

        assertNotNull(
                signatureNode
        );

        DOMValidateContext context =
                new DOMValidateContext(
                        certificate.getPublicKey(),
                        signatureNode
                );

        /*
         * SRI v2.34 still requires the legacy RSA-SHA1 profile.
         * Disable modern JDK secure-validation restrictions only
         * inside this compatibility test.
         */
        context.setProperty(
                "org.jcp.xml.dsig.secureValidation",
                Boolean.FALSE
        );

        XMLSignatureFactory factory =
                XMLSignatureFactory.getInstance(
                        "DOM"
                );

        XMLSignature signature =
                factory.unmarshalXMLSignature(
                        context
                );

        assertTrue(
                signature
                        .getSignatureValue()
                        .validate(
                                context
                        ),
                "XML SignatureValue is cryptographically invalid"
        );

        @SuppressWarnings("unchecked")
        List<Reference> references =
                signature
                        .getSignedInfo()
                        .getReferences();

        assertFalse(
                references.isEmpty()
        );

        for (
                Reference reference :
                references
        ) {

            assertTrue(
                    reference.validate(
                            context
                    ),
                    () ->
                            "Invalid XML signature reference: "
                                    + reference.getURI()
            );
        }

        assertTrue(
                signature.validate(
                        context
                ),
                "Complete XML signature validation failed"
        );
    }
}