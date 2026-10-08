
package io.github.imecuadorian.emitta.sriauthorization.integration;

import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.QueryDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.ReconcileDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.StoreAuthorizedDocumentXmlUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationPort;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;
import io.github.imecuadorian.emitta.support.JwtTestProperties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "emitta.sri.authorization.enabled=true",
        "emitta.sri.reception.enabled=false",
        "emitta.artifacts.storage.enabled=true",
        "emitta.fiscal.worker.enabled=false",
        "emitta.outbox.publisher.enabled=false",
        "emitta.fiscal.provider-ruc=1799999999001",
        "spring.datasource.hikari.schema=emitta"
})
@Testcontainers
@Tag("object-storage-integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SriAuthorizationWorkflowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("emitta_db")
                    .withUsername("emitta_test")
                    .withPassword("emitta_test");

    private static final Instant SRI_AUTHORIZED_AT =
            Instant.parse("2026-10-08T01:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StoreDocumentArtifactUseCase storeArtifact;

    @Autowired
    private ReconcileDocumentAuthorizationUseCase reconcile;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private StoreAuthorizedDocumentXmlUseCase storeAuthorizedDocumentXml;

    @Autowired
    private SriAuthorizationEvidencePort authorizationEvidencePort;

    @MockitoBean
    private SriAuthorizationPort sriAuthorizationPort;

    /*
     * Cryptographic verification is covered separately
     * by DssXadesBesXmlSignerTest.
     */
    @MockitoBean
    private XmlSignatureVerifierPort signatureVerifier;


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {

        JwtTestProperties.register(registry);

        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        String prefix = "emitta.artifacts.storage.";

        registry.add(prefix + "endpoint",
                () -> required("EMITTA_ARTIFACT_STORAGE_ENDPOINT"));

        registry.add(prefix + "region",
                () -> required("EMITTA_ARTIFACT_STORAGE_REGION"));

        registry.add(prefix + "bucket",
                () -> required("EMITTA_ARTIFACT_STORAGE_BUCKET"));

        registry.add(prefix + "access-key",
                () -> required("EMITTA_ARTIFACT_STORAGE_ACCESS_KEY"));

        registry.add(prefix + "secret-key",
                () -> required("EMITTA_ARTIFACT_STORAGE_SECRET_KEY"));

        registry.add(prefix + "path-style-access",
                () -> required("EMITTA_ARTIFACT_STORAGE_PATH_STYLE"));
    }

    @Test
    void shouldAuthorizeInvoiceAndPersistFiscalEvidence() {

        ensureBucketExists();

        UUID documentId = UUID.randomUUID();

        /*
         * Synthetic 49-digit key. This is not sent to SRI.
         */
        String accessKey = "%049d".formatted(
                new java.math.BigInteger(
                        documentId.toString().replace("-", ""),
                        16
                )
        );

        seedDocument(documentId, accessKey);

        /*
         * This fixture is intentionally synthetic.
         * The signature verifier is mocked in this test.
         */
        String xml = """
                <factura id="comprobante" version="2.1.0">
                  <infoTributaria>
                    <claveAcceso>%s</claveAcceso>
                  </infoTributaria>
                </factura>
                """.formatted(accessKey);

        byte[] xmlBytes =
                xml.getBytes(StandardCharsets.UTF_8);

        storeArtifact.store(
                new StoreDocumentArtifactCommand(
                        documentId,
                        DocumentArtifactType.SIGNED_XML,
                        "application/xml",
                        xmlBytes
                )
        );

        SriAuthorizationResult sriResponse =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.AUTHORIZED,
                        accessKey,
                        SRI_AUTHORIZED_AT,
                        xml,
                        List.of()
                );

        when(sriAuthorizationPort.query(
                any(SriAuthorizationRequest.class)
        )).thenReturn(sriResponse);

        /*
         * First execution: authorization must complete.
         */
        SriAuthorizationResult result =
                reconcile.reconcile(documentId);

        assertEquals(
                SriAuthorizationStatus.AUTHORIZED,
                result.status()
        );

        var document =
                jdbcTemplate.queryForMap("""
                        SELECT status, authorized_at
                        FROM emitta.documents
                        WHERE id = ?
                        """, documentId);

        assertEquals("AUTHORIZED", document.get("status"));

        assertEquals(
                SRI_AUTHORIZED_AT,
                ((Timestamp) document.get("authorized_at"))
                        .toInstant()
        );

        var authorization =
                jdbcTemplate.queryForMap("""
                        SELECT authorization_number,
                               authorized_at,
                               authorized_xml_sha256
                        FROM emitta.sri_authorizations
                        WHERE document_id = ?
                        """, documentId);

        assertEquals(
                accessKey,
                authorization.get("authorization_number")
        );

        assertEquals(
                SRI_AUTHORIZED_AT,
                ((Timestamp) authorization.get("authorized_at"))
                        .toInstant()
        );

        var artifact =
                jdbcTemplate.queryForMap("""
                        SELECT storage_key, sha256
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'AUTHORIZED_XML'
                        """, documentId);

        assertEquals(
                authorization.get("authorized_xml_sha256"),
                artifact.get("sha256")
        );

        /*
         * Confirm the actual bytes exist in MinIO.
         */
        byte[] storedBytes =
                s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(required(
                                        "EMITTA_ARTIFACT_STORAGE_BUCKET"
                                ))
                                .key((String) artifact.get("storage_key"))
                                .build()
                ).asByteArray();

        assertArrayEquals(xmlBytes, storedBytes);

        /*
         * Second execution: no duplicated fiscal evidence.
         */
        reconcile.reconcile(documentId);

        Integer authorizationCount =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM emitta.sri_authorizations
                        WHERE document_id = ?
                        """, Integer.class, documentId);

        Integer artifactCount =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'AUTHORIZED_XML'
                        """, Integer.class, documentId);

        assertEquals(1, authorizationCount);
        assertEquals(1, artifactCount);

        verify(sriAuthorizationPort, times(2))
                .query(any(SriAuthorizationRequest.class));
    }


    @Test
    void shouldRecoverAuthorizationWhenEvidenceExistsButDocumentIsPending() {

        ensureBucketExists();

        UUID documentId = UUID.randomUUID();

        String accessKey = "%049d".formatted(
                new java.math.BigInteger(
                        documentId.toString().replace("-", ""),
                        16
                )
        );

        seedDocument(documentId, accessKey);

        String xml = """
            <factura id="comprobante" version="2.1.0">
              <infoTributaria>
                <claveAcceso>%s</claveAcceso>
              </infoTributaria>
            </factura>
            """.formatted(accessKey);

        byte[] signedXml = xml.getBytes(StandardCharsets.UTF_8);

        storeArtifact.store(
                new StoreDocumentArtifactCommand(
                        documentId,
                        DocumentArtifactType.SIGNED_XML,
                        "application/xml",
                        signedXml
                )
        );

        SriAuthorizationResult sriResponse =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.AUTHORIZED,
                        accessKey,
                        SRI_AUTHORIZED_AT,
                        xml,
                        List.of()
                );

        /*
         * Simulate successful processing until the moment
         * immediately before updating the document status.
         */
        DocumentArtifact authorizedArtifact =
                storeAuthorizedDocumentXml.store(
                        documentId,
                        sriResponse
                );

        authorizationEvidencePort.save(
                new SriAuthorizationEvidence(
                        documentId,
                        accessKey,
                        SRI_AUTHORIZED_AT,
                        FiscalEnvironment.TEST,
                        authorizedArtifact.sha256(),
                        List.of()
                )
        );

        /*
         * Verify the interrupted state.
         * Fiscal evidence exists, but the document is pending.
         */
        String statusBefore =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM emitta.documents
                        WHERE id = ?
                        """,
                        String.class,
                        documentId
                );

        assertEquals("SUBMITTED", statusBefore);

        /*
         * Restart logical reconciliation.
         * Only the external SRI response is mocked.
         */
        when(sriAuthorizationPort.query(
                any(SriAuthorizationRequest.class)
        )).thenReturn(sriResponse);

        reconcile.reconcile(documentId);

        /*
         * The existing evidence must be reused.
         */
        var document =
                jdbcTemplate.queryForMap(
                        """
                        SELECT status, authorized_at
                        FROM emitta.documents
                        WHERE id = ?
                        """,
                        documentId
                );

        assertEquals("AUTHORIZED", document.get("status"));

        assertEquals(
                SRI_AUTHORIZED_AT,
                ((Timestamp) document.get("authorized_at"))
                        .toInstant()
        );

        Integer evidenceCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM emitta.sri_authorizations
                        WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId
                );

        Integer artifactCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM emitta.document_artifacts
                        WHERE document_id = ?
                          AND artifact_type = 'AUTHORIZED_XML'
                        """,
                        Integer.class,
                        documentId
                );

        assertEquals(1, evidenceCount);
        assertEquals(1, artifactCount);
    }


    private void seedDocument(UUID documentId, String accessKey) {

        UUID tenantId = UUID.randomUUID();
        UUID taxpayerId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID pointId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO emitta.tenants (id, name, status)
                VALUES (?, 'Authorization E2E', 'ACTIVE')
                """, tenantId);

        jdbcTemplate.update("""
                INSERT INTO emitta.taxpayers (
                    id, tenant_id, ruc, legal_name,
                    main_address, status,
                    test_enabled, production_enabled
                )
                VALUES (?, ?, '1790012345001',
                        'EMITTA TEST', 'Quito',
                        'ACTIVE', TRUE, FALSE)
                """, taxpayerId, tenantId);

        jdbcTemplate.update("""
                INSERT INTO emitta.establishments (
                    id, taxpayer_id, code, name,
                    address, status
                )
                VALUES (?, ?, '001', 'Matriz',
                        'Quito', 'ACTIVE')
                """, establishmentId, taxpayerId);

        jdbcTemplate.update("""
                INSERT INTO emitta.points_of_issue (
                    id, establishment_id, code, name, status
                )
                VALUES (?, ?, '001', 'Caja', 'ACTIVE')
                """, pointId, establishmentId);

        jdbcTemplate.update("""
                INSERT INTO emitta.documents (
                    id, tenant_id, taxpayer_id,
                    point_of_issue_id, document_type,
                    environment, status, access_key,
                    idempotency_key, issued_at
                )
                VALUES (
                    ?, ?, ?, ?, 'INVOICE',
                    'TEST', 'SUBMITTED', ?, ?, CURRENT_TIMESTAMP
                )
                """,
                documentId,
                tenantId,
                taxpayerId,
                pointId,
                accessKey,
                documentId.toString()
        );
    }

    private void ensureBucketExists() {

        String bucket = required(
                "EMITTA_ARTIFACT_STORAGE_BUCKET"
        );

        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(bucket)
                            .build()
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }

            s3Client.createBucket(
                    CreateBucketRequest.builder()
                            .bucket(bucket)
                            .build()
            );
        }
    }

    private static String required(String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }

        return value;
    }
}
