package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.AesGcmSecretCipher;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.CertificateSecretAssociatedData;
import io.github.imecuadorian.emitta.fiscalsigning.application.exception.SigningCertificateException;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.ResolvedSigningKeyMaterial;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgreSqlSigningKeyMaterialAdapterIntegrationTest {

    private static final Instant SIGNING_TIME =
            Instant.parse(
                    "2026-10-07T03:00:00Z"
            );

    /*
     * Test-only AES-256 key.
     * Production keys must come from secure external configuration.
     */
    private static final String MASTER_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            new byte[32]
                    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    "postgres:18"
            );

    private static JdbcClient jdbcClient;

    private static PostgreSqlSigningKeyMaterialAdapter adapter;

    private static AesGcmSecretCipher secretCipher;

    @BeforeAll
    static void setUpDatabase() {

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

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        new DataSourceTransactionManager(
                                dataSource
                        )
                );

        secretCipher =
                new AesGcmSecretCipher(
                        MASTER_KEY
                );

        adapter =
                new PostgreSqlSigningKeyMaterialAdapter(
                        jdbcClient,
                        transactionTemplate,
                        secretCipher
                );
    }

    @Test
    void shouldResolveDecryptAndAssignActiveSigningCertificate() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

        UUID establishmentId =
                UUID.randomUUID();

        UUID pointOfIssueId =
                UUID.randomUUID();

        UUID certificateId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        byte[] pkcs12Content =
                "TEST-PKCS12-CONTENT"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] passwordBytes =
                "test-password"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] encryptedContent =
                secretCipher.encrypt(
                        pkcs12Content,
                        CertificateSecretAssociatedData.content(
                                taxpayerId,
                                certificateId
                        )
                );

        byte[] encryptedPassword =
                secretCipher.encrypt(
                        passwordBytes,
                        CertificateSecretAssociatedData.password(
                                taxpayerId,
                                certificateId
                        )
                );

        seedFiscalHierarchy(
                tenantId,
                taxpayerId,
                establishmentId,
                pointOfIssueId
        );

        insertCertificate(
                certificateId,
                taxpayerId,
                encryptedContent,
                encryptedPassword
        );

        selectActiveCertificate(
                taxpayerId,
                certificateId
        );

        insertDocument(
                documentId,
                tenantId,
                taxpayerId,
                pointOfIssueId
        );

        ResolvedSigningKeyMaterial resolved =
                adapter.loadForDocument(
                        documentId,
                        SIGNING_TIME
                );

        assertEquals(
                certificateId,
                resolved.certificateId()
        );

        assertArrayEquals(
                pkcs12Content,
                resolved.keyMaterial()
                        .pkcs12Content()
        );

        assertArrayEquals(
                "test-password"
                        .toCharArray(),
                resolved.keyMaterial()
                        .password()
        );

        assertEquals(
                "emitta-test",
                resolved.keyMaterial()
                        .alias()
        );

        UUID persistedCertificateId =
                jdbcClient
                        .sql(
                                """
                                SELECT signing_certificate_id

                                FROM documents

                                WHERE id = :documentId
                                """
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                (
                                        resultSet,
                                        rowNumber
                                ) ->
                                        resultSet.getObject(
                                                "signing_certificate_id",
                                                UUID.class
                                        )
                        )
                        .single();

        assertEquals(
                certificateId,
                persistedCertificateId
        );
    }

    @Test
    void shouldKeepPreviouslyAssignedCertificateAfterTaxpayerRotation() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

        UUID establishmentId =
                UUID.randomUUID();

        UUID pointOfIssueId =
                UUID.randomUUID();

        UUID originalCertificateId =
                UUID.randomUUID();

        UUID rotatedCertificateId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        byte[] originalPkcs12 =
                "ORIGINAL-PKCS12"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] rotatedPkcs12 =
                "ROTATED-PKCS12"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] password =
                "test-password"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        seedFiscalHierarchy(
                tenantId,
                taxpayerId,
                establishmentId,
                pointOfIssueId
        );

        insertCertificate(
                originalCertificateId,
                taxpayerId,
                secretCipher.encrypt(
                        originalPkcs12,
                        CertificateSecretAssociatedData.content(
                                taxpayerId,
                                originalCertificateId
                        )
                ),
                secretCipher.encrypt(
                        password,
                        CertificateSecretAssociatedData.password(
                                taxpayerId,
                                originalCertificateId
                        )
                )
        );

        insertCertificate(
                rotatedCertificateId,
                taxpayerId,
                secretCipher.encrypt(
                        rotatedPkcs12,
                        CertificateSecretAssociatedData.content(
                                taxpayerId,
                                rotatedCertificateId
                        )
                ),
                secretCipher.encrypt(
                        password,
                        CertificateSecretAssociatedData.password(
                                taxpayerId,
                                rotatedCertificateId
                        )
                )
        );

        /*
         * New documents would now use the rotated certificate.
         */
        selectActiveCertificate(
                taxpayerId,
                rotatedCertificateId
        );

        insertDocument(
                documentId,
                tenantId,
                taxpayerId,
                pointOfIssueId
        );

        /*
         * Simulate that this document had already selected the
         * previous certificate before the taxpayer rotated it.
         */
        jdbcClient
                .sql(
                        """
                        UPDATE documents
    
                        SET signing_certificate_id =
                            :certificateId
    
                        WHERE id = :documentId
                        """
                )
                .param(
                        "certificateId",
                        originalCertificateId
                )
                .param(
                        "documentId",
                        documentId
                )
                .update();

        ResolvedSigningKeyMaterial resolved =
                adapter.loadForDocument(
                        documentId,
                        SIGNING_TIME
                );

        /*
         * Redelivery/retry must reuse the certificate already
         * assigned to the fiscal document.
         */
        assertEquals(
                originalCertificateId,
                resolved.certificateId()
        );

        assertArrayEquals(
                originalPkcs12,
                resolved.keyMaterial()
                        .pkcs12Content()
        );

        UUID persistedCertificateId =
                jdbcClient
                        .sql(
                                """
                                SELECT signing_certificate_id
    
                                FROM documents
    
                                WHERE id = :documentId
                                """
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                (
                                        resultSet,
                                        rowNumber
                                ) ->
                                        resultSet.getObject(
                                                "signing_certificate_id",
                                                UUID.class
                                        )
                        )
                        .single();

        assertEquals(
                originalCertificateId,
                persistedCertificateId
        );
    }

    @Test
    void shouldRejectExpiredSigningCertificate() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

        UUID establishmentId =
                UUID.randomUUID();

        UUID pointOfIssueId =
                UUID.randomUUID();

        UUID certificateId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        byte[] pkcs12Content =
                "EXPIRED-PKCS12"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] password =
                "test-password"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        seedFiscalHierarchy(
                tenantId,
                taxpayerId,
                establishmentId,
                pointOfIssueId
        );

        insertCertificate(
                certificateId,
                taxpayerId,
                secretCipher.encrypt(
                        pkcs12Content,
                        CertificateSecretAssociatedData.content(
                                taxpayerId,
                                certificateId
                        )
                ),
                secretCipher.encrypt(
                        password,
                        CertificateSecretAssociatedData.password(
                                taxpayerId,
                                certificateId
                        )
                ),
                "ACTIVE",
                SIGNING_TIME.minus(
                        365,
                        ChronoUnit.DAYS
                ),
                SIGNING_TIME.minus(
                        1,
                        ChronoUnit.SECONDS
                )
        );

        selectActiveCertificate(
                taxpayerId,
                certificateId
        );

        insertDocument(
                documentId,
                tenantId,
                taxpayerId,
                pointOfIssueId
        );

        SigningCertificateException exception =
                assertThrows(
                        SigningCertificateException.class,
                        () ->
                                adapter.loadForDocument(
                                        documentId,
                                        SIGNING_TIME
                                )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "expired"
                        )
        );

        assertDocumentHasNoAssignedCertificate(
                documentId
        );
    }

    @Test
    void shouldRejectDisabledSigningCertificate() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

        UUID establishmentId =
                UUID.randomUUID();

        UUID pointOfIssueId =
                UUID.randomUUID();

        UUID certificateId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        byte[] pkcs12Content =
                "DISABLED-PKCS12"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] password =
                "test-password"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        seedFiscalHierarchy(
                tenantId,
                taxpayerId,
                establishmentId,
                pointOfIssueId
        );

        insertCertificate(
                certificateId,
                taxpayerId,
                secretCipher.encrypt(
                        pkcs12Content,
                        CertificateSecretAssociatedData.content(
                                taxpayerId,
                                certificateId
                        )
                ),
                secretCipher.encrypt(
                        password,
                        CertificateSecretAssociatedData.password(
                                taxpayerId,
                                certificateId
                        )
                ),
                "DISABLED",
                SIGNING_TIME.minus(
                        1,
                        ChronoUnit.DAYS
                ),
                SIGNING_TIME.plus(
                        365,
                        ChronoUnit.DAYS
                )
        );

        selectActiveCertificate(
                taxpayerId,
                certificateId
        );

        insertDocument(
                documentId,
                tenantId,
                taxpayerId,
                pointOfIssueId
        );

        SigningCertificateException exception =
                assertThrows(
                        SigningCertificateException.class,
                        () ->
                                adapter.loadForDocument(
                                        documentId,
                                        SIGNING_TIME
                                )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "not ACTIVE"
                        )
        );

        assertDocumentHasNoAssignedCertificate(
                documentId
        );
    }

    @Test
    void shouldRejectDocumentWhenTaxpayerHasNoSigningCertificateConfigured() {

        UUID tenantId =
                UUID.randomUUID();

        UUID taxpayerId =
                UUID.randomUUID();

        UUID establishmentId =
                UUID.randomUUID();

        UUID pointOfIssueId =
                UUID.randomUUID();

        UUID documentId =
                UUID.randomUUID();

        seedFiscalHierarchy(
                tenantId,
                taxpayerId,
                establishmentId,
                pointOfIssueId
        );

        insertDocument(
                documentId,
                tenantId,
                taxpayerId,
                pointOfIssueId
        );

        SigningCertificateException exception =
                assertThrows(
                        SigningCertificateException.class,
                        () ->
                                adapter.loadForDocument(
                                        documentId,
                                        SIGNING_TIME
                                )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "no active signing certificate"
                        )
        );

        assertDocumentHasNoAssignedCertificate(
                documentId
        );
    }

    private static void insertCertificate(
            UUID certificateId,
            UUID taxpayerId,
            byte[] encryptedContent,
            byte[] encryptedPassword
    ) {

        insertCertificate(
                certificateId,
                taxpayerId,
                encryptedContent,
                encryptedPassword,
                "ACTIVE",
                SIGNING_TIME.minus(
                        1,
                        ChronoUnit.DAYS
                ),
                SIGNING_TIME.plus(
                        365,
                        ChronoUnit.DAYS
                )
        );
    }

    private static void insertCertificate(
            UUID certificateId,
            UUID taxpayerId,
            byte[] encryptedContent,
            byte[] encryptedPassword,
            String status,
            Instant validFrom,
            Instant validUntil
    ) {

        jdbcClient
                .sql(
                        """
                        INSERT INTO certificates (
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
                        VALUES (
                            :id,
                            :taxpayerId,
                            'emitta-test',
                            :fingerprint,
                            'CN=Emitta Test',
                            'CN=Emitta Test CA',
                            :validFrom,
                            :validUntil,
                            :status,
                            :encryptedContent,
                            :encryptedPassword
                        )
                        """
                )
                .param(
                        "id",
                        certificateId
                )
                .param(
                        "taxpayerId",
                        taxpayerId
                )
                .param(
                        "fingerprint",
                        "integration-"
                                + certificateId
                )
                .param(
                        "validFrom",
                        Timestamp.from(
                                validFrom
                        )
                )
                .param(
                        "validUntil",
                        Timestamp.from(
                                validUntil
                        )
                )
                .param(
                        "status",
                        status
                )
                .param(
                        "encryptedContent",
                        encryptedContent
                )
                .param(
                        "encryptedPassword",
                        encryptedPassword
                )
                .update();
    }

    private static void seedFiscalHierarchy(
            UUID tenantId,
            UUID taxpayerId,
            UUID establishmentId,
            UUID pointOfIssueId
    ) {

        jdbcClient
                .sql(
                        """
                        INSERT INTO tenants (
                            id,
                            name,
                            status
                        )
                        VALUES (
                            :id,
                            'Signing Integration Tenant',
                            'ACTIVE'
                        )
                        """
                )
                .param(
                        "id",
                        tenantId
                )
                .update();

        jdbcClient
                .sql(
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
                            'EMITTA SIGNING TEST S.A.S.',
                            'EMITTA SIGNING TEST',
                            'Quito, Ecuador',
                            'ACTIVE',
                            TRUE,
                            FALSE
                        )
                        """
                )
                .param(
                        "id",
                        taxpayerId
                )
                .param(
                        "tenantId",
                        tenantId
                )
                .update();

        jdbcClient
                .sql(
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
                        establishmentId
                )
                .param(
                        "taxpayerId",
                        taxpayerId
                )
                .update();

        jdbcClient
                .sql(
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
                        pointOfIssueId
                )
                .param(
                        "establishmentId",
                        establishmentId
                )
                .update();
    }


    private static void assertDocumentHasNoAssignedCertificate(
            UUID documentId
    ) {

        Boolean unassigned =
                jdbcClient
                        .sql(
                                """
                                SELECT signing_certificate_id IS NULL
    
                                FROM documents
    
                                WHERE id = :documentId
                                """
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                Boolean.class
                        )
                        .single();

        assertTrue(
                unassigned
        );
    }

    private static void selectActiveCertificate(
            UUID taxpayerId,
            UUID certificateId
    ) {

        jdbcClient
                .sql(
                        """
                        UPDATE taxpayers

                        SET active_signing_certificate_id =
                            :certificateId

                        WHERE id = :taxpayerId
                        """
                )
                .param(
                        "certificateId",
                        certificateId
                )
                .param(
                        "taxpayerId",
                        taxpayerId
                )
                .update();
    }

    private static void insertDocument(
            UUID documentId,
            UUID tenantId,
            UUID taxpayerId,
            UUID pointOfIssueId
    ) {

        jdbcClient
                .sql(
                        """
                        INSERT INTO documents (
                            id,
                            tenant_id,
                            taxpayer_id,
                            point_of_issue_id,
                            document_type,
                            environment,
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
                            'GENERATING',
                            :idempotencyKey,
                            :issuedAt
                        )
                        """
                )
                .param(
                        "id",
                        documentId
                )
                .param(
                        "tenantId",
                        tenantId
                )
                .param(
                        "taxpayerId",
                        taxpayerId
                )
                .param(
                        "pointOfIssueId",
                        pointOfIssueId
                )
                .param(
                        "idempotencyKey",
                        "signing-integration-"
                                + documentId
                )
                .param(
                        "issuedAt",
                        Timestamp.from(
                                SIGNING_TIME
                        )
                )
                .update();
    }
}