package io.github.imecuadorian.emitta.invoicexml.integration;

import io.github.imecuadorian.emitta.documentartifact.adapter.config.ArtifactStorageProperties;
import io.github.imecuadorian.emitta.documentartifact.adapter.out.persistence.PostgreSqlDocumentArtifactRepositoryAdapter;
import io.github.imecuadorian.emitta.documentartifact.adapter.out.storage.S3DocumentArtifactStorageAdapter;
import io.github.imecuadorian.emitta.documentartifact.application.service.StoreDocumentArtifactService;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.invoicexml.adapter.out.persistence.PostgreSqlInvoiceXmlSourceAdapter;
import io.github.imecuadorian.emitta.invoicexml.adapter.out.xml.SriInvoiceXmlGenerator;
import io.github.imecuadorian.emitta.invoicexml.adapter.out.xml.SriInvoiceXsdValidator;
import io.github.imecuadorian.emitta.invoicexml.application.service.GenerateAndStoreInvoiceXmlService;
import io.github.imecuadorian.emitta.invoicexml.application.service.GenerateInvoiceXmlService;

import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.sql.DataSource;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("object-storage-integration")
class InvoiceXmlArtifactIntegrationTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "88888888-8888-8888-8888-888888888888"
            );

    private static final UUID ITEM_ID =
            UUID.fromString(
                    "99999999-9999-9999-9999-999999999999"
            );

    private static final String ACCESS_KEY =
            "0610202601179001234500110010010000000028055561612";

    private static final String PROVIDER_RUC =
            "1799999999001";

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-07T02:00:00Z"
            );

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:18"
            );

    private static JdbcClient jdbcClient;

    private static S3Client s3Client;

    private static ArtifactStorageProperties storageProperties;

    @BeforeAll
    static void setUp() {

        configurePostgreSql();

        configureObjectStorage();

        seedInvoice();
    }

    @AfterAll
    static void tearDown() {

        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    void shouldGenerateValidateAndPersistRealInvoiceXml()
            throws Exception {

        PostgreSqlInvoiceXmlSourceAdapter sourceAdapter =
                new PostgreSqlInvoiceXmlSourceAdapter(
                        jdbcClient
                );

        GenerateInvoiceXmlService generateService =
                new GenerateInvoiceXmlService(
                        sourceAdapter,
                        new SriInvoiceXmlGenerator(),
                        PROVIDER_RUC,
                        ZoneId.of(
                                "America/Guayaquil"
                        )
                );

        PostgreSqlDocumentArtifactRepositoryAdapter
                repositoryAdapter =
                new PostgreSqlDocumentArtifactRepositoryAdapter(
                        jdbcClient
                );

        S3DocumentArtifactStorageAdapter storageAdapter =
                new S3DocumentArtifactStorageAdapter(
                        s3Client,
                        storageProperties
                );

        StoreDocumentArtifactService storeService =
                new StoreDocumentArtifactService(
                        repositoryAdapter,
                        storageAdapter,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );

        SriInvoiceXsdValidator validator =
                new SriInvoiceXsdValidator();

        GenerateAndStoreInvoiceXmlService service =
                new GenerateAndStoreInvoiceXmlService(
                        generateService,
                        validator,
                        storeService
                );

        /*
         * Generate independently so we know exactly which bytes
         * must later exist in object storage.
         */
        GeneratedInvoiceXml expected =
                generateService.generate(
                        DOCUMENT_ID
                );

        validator.validate(
                expected.bytes()
        );

        DocumentArtifact artifact =
                service.generateAndStore(
                        DOCUMENT_ID
                );

        assertEquals(
                DOCUMENT_ID,
                artifact.documentId()
        );

        assertEquals(
                DocumentArtifactType.UNSIGNED_XML,
                artifact.type()
        );

        assertEquals(
                "application/xml",
                artifact.contentType()
        );

        assertEquals(
                "documents/"
                        + DOCUMENT_ID
                        + "/unsigned.xml",
                artifact.storageKey()
        );

        assertEquals(
                expected.bytes().length,
                artifact.sizeBytes()
        );

        assertEquals(
                sha256(
                        expected.bytes()
                ),
                artifact.sha256()
        );

        assertEquals(
                NOW,
                artifact.createdAt()
        );

        DocumentArtifact persisted =
                repositoryAdapter
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
                        .orElseThrow();

        assertEquals(
                artifact,
                persisted
        );

        try {

            ResponseBytes<GetObjectResponse> storedObject =
                    s3Client.getObjectAsBytes(
                            GetObjectRequest.builder()
                                    .bucket(
                                            storageProperties.bucket()
                                    )
                                    .key(
                                            artifact.storageKey()
                                    )
                                    .build()
                    );

            byte[] downloaded =
                    storedObject.asByteArray();

            assertArrayEquals(
                    expected.bytes(),
                    downloaded
            );

            assertEquals(
                    artifact.sha256(),
                    sha256(
                            downloaded
                    )
            );

            assertEquals(
                    "application/xml",
                    storedObject
                            .response()
                            .contentType()
            );

            assertEquals(
                    artifact.sha256(),
                    storedObject
                            .response()
                            .metadata()
                            .get(
                                    "sha256"
                            )
            );

            assertEquals(
                    DOCUMENT_ID.toString(),
                    storedObject
                            .response()
                            .metadata()
                            .get(
                                    "document-id"
                            )
            );

            assertEquals(
                    "UNSIGNED_XML",
                    storedObject
                            .response()
                            .metadata()
                            .get(
                                    "artifact-type"
                            )
            );

            /*
             * Validate the exact object retrieved from MinIO.
             */
            validator.validate(
                    downloaded
            );

            String xml =
                    new String(
                            downloaded,
                            java.nio.charset.StandardCharsets.UTF_8
                    );

            assertTrue(
                    xml.contains(
                            ACCESS_KEY
                    )
            );

            assertTrue(
                    xml.contains(
                            "<razonSocial>FAXORF LOCAL DEVELOPMENT S.A.S.</razonSocial>"
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
                                    + PROVIDER_RUC
                                    + "</campoAdicional>"
                    )
            );

        } finally {

            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(
                                    storageProperties.bucket()
                            )
                            .key(
                                    artifact.storageKey()
                            )
                            .build()
            );
        }
    }

    private static void configurePostgreSql() {

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .schemas(
                        "emitta"
                )
                .defaultSchema(
                        "emitta"
                )
                .createSchemas(
                        true
                )
                .locations(
                        "classpath:db/migration"
                )
                .load()
                .migrate();

        String jdbcUrl =
                POSTGRES.getJdbcUrl();

        String separator =
                jdbcUrl.contains("?")
                        ? "&"
                        : "?";

        String emittaJdbcUrl =
                jdbcUrl
                        + separator
                        + "currentSchema=emitta";

        DataSource dataSource =
                new DriverManagerDataSource(
                        emittaJdbcUrl,
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                );

        jdbcClient =
                JdbcClient.create(
                        dataSource
                );
    }

    private static void configureObjectStorage() {

        storageProperties =
                new ArtifactStorageProperties(
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_ENDPOINT"
                        ),
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_REGION"
                        ),
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_BUCKET"
                        ),
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_ACCESS_KEY"
                        ),
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_SECRET_KEY"
                        ),
                        Boolean.parseBoolean(
                                requiredEnvironmentVariable(
                                        "EMITTA_ARTIFACT_STORAGE_PATH_STYLE"
                                )
                        )
                );

        s3Client =
                S3Client.builder()
                        .endpointOverride(
                                URI.create(
                                        storageProperties.endpoint()
                                )
                        )
                        .region(
                                Region.of(
                                        storageProperties.region()
                                )
                        )
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                storageProperties.accessKey(),
                                                storageProperties.secretKey()
                                        )
                                )
                        )
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(
                                                storageProperties
                                                        .pathStyleAccess()
                                        )
                                        .build()
                        )
                        .httpClientBuilder(
                                UrlConnectionHttpClient.builder()
                        )
                        .build();

        ensureBucketExists();
    }

    private static void ensureBucketExists() {

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

    private static void seedInvoice() {

        jdbcClient.sql(
                        """
                        INSERT INTO tenants (
                            id,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            'Emitta Artifact E2E',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        TENANT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO taxpayers (
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
                        VALUES (
                            :id,
                            :tenantId,
                            '1790012345001',
                            'FAXORF LOCAL DEVELOPMENT S.A.S.',
                            'FAXORF',
                            'Quito, Ecuador',
                            'ACTIVE',
                            TRUE,
                            FALSE
                        )
                        """
                )
                .param(
                        "id",
                        TAXPAYER_ID
                )
                .param(
                        "tenantId",
                        TENANT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO establishments (
                            id,
                            taxpayer_id,
                            code,
                            name,
                            address,
                            status
                        )
                        VALUES (
                            :id,
                            :taxpayerId,
                            '001',
                            'Matriz',
                            'Quito, Ecuador',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        ESTABLISHMENT_ID
                )
                .param(
                        "taxpayerId",
                        TAXPAYER_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO points_of_issue (
                            id,
                            establishment_id,
                            code,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            :establishmentId,
                            '001',
                            'Caja Principal',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        POINT_OF_ISSUE_ID
                )
                .param(
                        "establishmentId",
                        ESTABLISHMENT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO documents (
                            id,
                            tenant_id,
                            taxpayer_id,
                            point_of_issue_id,
                            document_type,
                            environment,
                            sequential,
                            access_key,
                            status,
                            idempotency_key,
                            issued_at
                        )
                        VALUES (
                            :id,
                            :tenantId,
                            :taxpayerId,
                            :pointOfIssueId,
                            'INVOICE',
                            'TEST',
                            2,
                            :accessKey,
                            'GENERATING',
                            'invoice-artifact-e2e',
                            TIMESTAMPTZ '2026-10-06 23:00:00+00'
                        )
                        """
                )
                .param(
                        "id",
                        DOCUMENT_ID
                )
                .param(
                        "tenantId",
                        TENANT_ID
                )
                .param(
                        "taxpayerId",
                        TAXPAYER_ID
                )
                .param(
                        "pointOfIssueId",
                        POINT_OF_ISSUE_ID
                )
                .param(
                        "accessKey",
                        ACCESS_KEY
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoices (
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
                        VALUES (
                            :documentId,
                            '07',
                            '9999999999999',
                            'CONSUMIDOR FINAL',
                            18.00,
                            2.00,
                            2.70,
                            20.70,
                            'DOLAR'
                        )
                        """
                )
                .param(
                        "documentId",
                        DOCUMENT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoice_items (
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
                        VALUES (
                            :id,
                            :invoiceId,
                            1,
                            'P001',
                            'Producto de prueba',
                            2.000000,
                            10.000000,
                            2.00,
                            18.00,
                            2.70,
                            20.70
                        )
                        """
                )
                .param(
                        "id",
                        ITEM_ID
                )
                .param(
                        "invoiceId",
                        DOCUMENT_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoice_item_taxes (
                            invoice_item_id,
                            tax_code,
                            percentage_code,
                            rate,
                            taxable_base,
                            tax_amount
                        )
                        VALUES (
                            :invoiceItemId,
                            '2',
                            '4',
                            15.0000,
                            18.00,
                            2.70
                        )
                        """
                )
                .param(
                        "invoiceItemId",
                        ITEM_ID
                )
                .update();

        jdbcClient.sql(
                        """
                        INSERT INTO invoice_payments (
                            invoice_id,
                            line_number,
                            payment_method,
                            total
                        )
                        VALUES (
                            :invoiceId,
                            1,
                            '01',
                            20.70
                        )
                        """
                )
                .param(
                        "invoiceId",
                        DOCUMENT_ID
                )
                .update();
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

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    "Required environment variable is missing: "
                            + name
            );
        }

        return value;
    }
}