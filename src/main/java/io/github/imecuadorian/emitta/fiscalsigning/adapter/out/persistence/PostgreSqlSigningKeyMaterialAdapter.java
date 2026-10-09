package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.AesGcmSecretCipher;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.CertificateSecretAssociatedData;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.Pkcs12CertificateValidator;
import io.github.imecuadorian.emitta.fiscalsigning.application.exception.SigningCertificateException;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.ResolvedSigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.SigningKeyMaterialPort;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PostgreSqlSigningKeyMaterialAdapter
        implements SigningKeyMaterialPort {

    private static final String FIND_DOCUMENT_SQL = """
            SELECT
                d.taxpayer_id,
                t.ruc,
                d.signing_certificate_id,
                t.active_signing_certificate_id

            FROM documents d

            JOIN taxpayers t
              ON t.id = d.taxpayer_id
             AND t.tenant_id = d.tenant_id

            WHERE d.id = :documentId

            FOR UPDATE OF d
            """;

    private static final String FIND_CERTIFICATE_SQL = """
            SELECT
                id,
                taxpayer_id,
                alias,
                status,
                valid_from,
                valid_until,
                encrypted_content,
                encrypted_password,
                fingerprint,
                authorized_ruc,
                authorization_evidence_sha256,
                authorization_trust_anchor

            FROM certificates

            WHERE id = :certificateId
              AND taxpayer_id = :taxpayerId
            """;

    private static final String ASSIGN_CERTIFICATE_SQL = """
            UPDATE documents

            SET
                signing_certificate_id = :certificateId,
                updated_at = CURRENT_TIMESTAMP

            WHERE id = :documentId
              AND signing_certificate_id IS NULL
            """;

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;
    private final AesGcmSecretCipher secretCipher;

    public PostgreSqlSigningKeyMaterialAdapter(
            JdbcClient jdbcClient,
            TransactionTemplate transactionTemplate,
            AesGcmSecretCipher secretCipher
    ) {

        this.jdbcClient =
                Objects.requireNonNull(
                        jdbcClient,
                        "JdbcClient cannot be null"
                );

        this.transactionTemplate =
                Objects.requireNonNull(
                        transactionTemplate,
                        "TransactionTemplate cannot be null"
                );

        this.secretCipher =
                Objects.requireNonNull(
                        secretCipher,
                        "Secret cipher cannot be null"
                );
    }

    @Override
    public ResolvedSigningKeyMaterial loadForDocument(
            UUID documentId,
            Instant signingTime
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                signingTime,
                "Signing time cannot be null"
        );

        ResolvedSigningKeyMaterial result =
                transactionTemplate.execute(
                        status ->
                                loadInTransaction(
                                        documentId,
                                        signingTime
                                )
                );

        if (result == null) {
            throw new SigningCertificateException(
                    "Unable to resolve signing certificate for document: "
                            + documentId
            );
        }

        return result;
    }

    private ResolvedSigningKeyMaterial loadInTransaction(
            UUID documentId,
            Instant signingTime
    ) {

        DocumentCertificateSelection selection =
                jdbcClient
                        .sql(
                                FIND_DOCUMENT_SQL
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .query(
                                this::mapDocumentSelection
                        )
                        .optional()
                        .orElseThrow(
                                () ->
                                        new SigningCertificateException(
                                                "Document not found: "
                                                        + documentId
                                        )
                        );

        UUID certificateId =
                resolveCertificateId(
                        selection,
                        documentId
                );

        CertificateRow certificate =
                jdbcClient
                        .sql(
                                FIND_CERTIFICATE_SQL
                        )
                        .param(
                                "certificateId",
                                certificateId
                        )
                        .param(
                                "taxpayerId",
                                selection.taxpayerId()
                        )
                        .query(
                                this::mapCertificate
                        )
                        .optional()
                        .orElseThrow(
                                () ->
                                        new SigningCertificateException(
                                                "Signing certificate not found for document: "
                                                        + documentId
                                        )
                        );

        validateCertificate(
                certificate,
                signingTime,
                documentId
        );

        SigningKeyMaterial keyMaterial = validateMaterial(certificate, selection.ruc(), signingTime);

        if (selection.signingCertificateId() == null) {
            try {
                jdbcClient.sql(ASSIGN_CERTIFICATE_SQL).param("certificateId", certificateId)
                        .param("documentId", documentId).update();
            } catch (RuntimeException failure) {
                keyMaterial.close();
                throw failure;
            }
        }
        return new ResolvedSigningKeyMaterial(certificate.id(), keyMaterial);
    }

    public ResolvedSigningKeyMaterial loadSelectedForTaxpayer(UUID tenantId, UUID taxpayerId, Instant time) {
        return transactionTemplate.execute(status -> {
            var row = jdbcClient.sql("""
                    SELECT active_signing_certificate_id, ruc FROM taxpayers
                    WHERE id=:taxpayer AND tenant_id=:tenant AND status='ACTIVE' AND test_enabled=true
                    """).param("taxpayer", taxpayerId).param("tenant", tenantId).query().singleRow();
            UUID id = (UUID) row.get("active_signing_certificate_id");
            if (id == null) throw new SigningCertificateException("No active certificate selected");
            CertificateRow certificate = jdbcClient.sql(FIND_CERTIFICATE_SQL).param("certificateId",id)
                    .param("taxpayerId",taxpayerId).query(this::mapCertificate).single();
            validateCertificate(certificate,time,taxpayerId);
            return new ResolvedSigningKeyMaterial(id,validateMaterial(certificate,(String)row.get("ruc"),time));
        });
    }

    private SigningKeyMaterial validateMaterial(CertificateRow certificate, String ruc, Instant signingTime) {
        if (!ruc.equals(certificate.authorizedRuc()) || certificate.authorizationEvidence() == null
                || certificate.trustAnchor() == null) {
            throw new SigningCertificateException("Certificate has no reviewed authority for taxpayer RUC");
        }
        SigningKeyMaterial keyMaterial = decryptKeyMaterial(certificate);
        try {
            var anchor = (java.security.cert.X509Certificate) java.security.cert.CertificateFactory
                    .getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(certificate.trustAnchor()));
            var validated = Pkcs12CertificateValidator.validate(keyMaterial, signingTime, java.util.List.of(anchor));
            Pkcs12CertificateValidator.requireCertifiedRuc(validated.certificate(), ruc);
            if (!certificate.fingerprint().equals(Pkcs12CertificateValidator.sha256(validated.certificate().getEncoded())))
                throw new SigningCertificateException("Certificate fingerprint does not match stored identity");
        } catch (Exception failure) {
            keyMaterial.close();
            if (failure instanceof SigningCertificateException signingFailure) throw signingFailure;
            throw new SigningCertificateException("Unable to validate authorized certificate", failure);
        }

        return keyMaterial;
    }

    private static UUID resolveCertificateId(
            DocumentCertificateSelection selection,
            UUID documentId
    ) {

        if (
                selection.signingCertificateId()
                        != null
        ) {

            return selection.signingCertificateId();
        }

        if (
                selection.activeSigningCertificateId()
                        == null
        ) {

            throw new SigningCertificateException(
                    "Taxpayer has no active signing certificate configured for document: "
                            + documentId
            );
        }

        return selection.activeSigningCertificateId();
    }

    /** Local operator-only onboarding. Never exposed as a web endpoint. */
    public UUID registerReviewedCertificate(UUID tenantId, UUID taxpayerId, String expectedRuc,
            SigningKeyMaterial material, Pkcs12CertificateValidator.Validated validated,
            String evidenceSha256, Instant now) {
        return transactionTemplate.execute(status -> {
            String ruc = jdbcClient.sql("""
                    SELECT ruc FROM taxpayers WHERE id=:taxpayer AND tenant_id=:tenant
                      AND status='ACTIVE' AND test_enabled=true FOR UPDATE
                    """).param("taxpayer", taxpayerId).param("tenant", tenantId)
                    .query(String.class).single();
            if (!ruc.equals(expectedRuc)) throw new SigningCertificateException("Reviewed RUC changed");
            byte[] content = material.pkcs12Content();
            char[] password = material.password();
            byte[] passwordBytes = null;
            try {
                String fingerprint = Pkcs12CertificateValidator.sha256(validated.certificate().getEncoded());
                UUID id = jdbcClient.sql("SELECT id FROM certificates WHERE taxpayer_id=:taxpayer AND fingerprint=:fp")
                        .param("taxpayer", taxpayerId).param("fp", fingerprint).query(UUID.class)
                        .optional().orElseGet(UUID::randomUUID);
                java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
                passwordBytes = new byte[encoded.remaining()];
                encoded.get(passwordBytes);
                if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
                jdbcClient.sql("""
                        INSERT INTO certificates (id,taxpayer_id,alias,fingerprint,subject,issuer,
                            valid_from,valid_until,status,encrypted_content,encrypted_password,
                            authorized_ruc,authorization_evidence_sha256,authorized_at,authorization_trust_anchor)
                        VALUES (:id,:taxpayer,:alias,:fp,:subject,:issuer,:from,:until,'ACTIVE',:content,:password,
                            :ruc,:evidence,:now,:anchor)
                        ON CONFLICT (taxpayer_id,fingerprint) DO UPDATE SET
                            alias=EXCLUDED.alias, encrypted_content=EXCLUDED.encrypted_content,
                            encrypted_password=EXCLUDED.encrypted_password,
                            authorized_ruc=EXCLUDED.authorized_ruc,
                            authorization_evidence_sha256=EXCLUDED.authorization_evidence_sha256,
                            authorized_at=EXCLUDED.authorized_at,
                            authorization_trust_anchor=EXCLUDED.authorization_trust_anchor,
                            updated_at=CURRENT_TIMESTAMP
                        WHERE certificates.status='ACTIVE'
                        """).param("id", id).param("taxpayer", taxpayerId).param("alias", validated.alias())
                        .param("fp", fingerprint).param("subject", validated.certificate().getSubjectX500Principal().getName())
                        .param("issuer", validated.certificate().getIssuerX500Principal().getName())
                        .param("from", java.sql.Timestamp.from(validated.certificate().getNotBefore().toInstant()))
                        .param("until", java.sql.Timestamp.from(validated.certificate().getNotAfter().toInstant()))
                        .param("content", secretCipher.encrypt(content, CertificateSecretAssociatedData.content(taxpayerId,id)))
                        .param("password", secretCipher.encrypt(passwordBytes, CertificateSecretAssociatedData.password(taxpayerId,id)))
                        .param("ruc", ruc).param("evidence", evidenceSha256).param("now", java.sql.Timestamp.from(now))
                        .param("anchor", validated.trustAnchor().getEncoded()).update();
                if (!"ACTIVE".equals(jdbcClient.sql("SELECT status FROM certificates WHERE id=:id")
                        .param("id", id).query(String.class).single()))
                    throw new SigningCertificateException("Cannot reactivate disabled or revoked certificate");
                jdbcClient.sql("UPDATE taxpayers SET active_signing_certificate_id=:id WHERE id=:taxpayer AND tenant_id=:tenant")
                        .param("id", id).param("taxpayer", taxpayerId).param("tenant", tenantId).update();
                return id;
            } catch (java.security.cert.CertificateEncodingException e) {
                throw new SigningCertificateException("Cannot encode certificate identity", e);
            } finally {
                Arrays.fill(content, (byte) 0);
                Arrays.fill(password, '\0');
                if (passwordBytes != null) Arrays.fill(passwordBytes, (byte) 0);
            }
        });
    }

    private static void validateCertificate(
            CertificateRow certificate,
            Instant signingTime,
            UUID documentId
    ) {

        if (
                !"ACTIVE".equals(
                        certificate.status()
                )
        ) {

            throw new SigningCertificateException(
                    "Signing certificate is not ACTIVE for document: "
                            + documentId
            );
        }

        if (
                signingTime.isBefore(
                        certificate.validFrom()
                )
        ) {

            throw new SigningCertificateException(
                    "Signing certificate is not valid yet for document: "
                            + documentId
            );
        }

        if (
                signingTime.isAfter(
                        certificate.validUntil()
                )
        ) {

            throw new SigningCertificateException(
                    "Signing certificate has expired for document: "
                            + documentId
            );
        }
    }

    private SigningKeyMaterial decryptKeyMaterial(
            CertificateRow certificate
    ) {

        byte[] pkcs12Content =
                null;

        byte[] passwordBytes =
                null;

        char[] password =
                null;

        try {

            pkcs12Content =
                    secretCipher.decrypt(
                            certificate.encryptedContent(),
                            CertificateSecretAssociatedData.content(
                                    certificate.taxpayerId(),
                                    certificate.id()
                            )
                    );

            passwordBytes =
                    secretCipher.decrypt(
                            certificate.encryptedPassword(),
                            CertificateSecretAssociatedData.password(
                                    certificate.taxpayerId(),
                                    certificate.id()
                            )
                    );

            password =
                    decodePassword(
                            passwordBytes
                    );

            return new SigningKeyMaterial(
                    pkcs12Content,
                    password,
                    certificate.alias()
            );

        } catch (SigningCertificateException exception) {

            throw exception;

        } catch (RuntimeException exception) {

            throw new SigningCertificateException(
                    "Unable to decrypt signing certificate material",
                    exception
            );

        } finally {

            if (pkcs12Content != null) {
                Arrays.fill(
                        pkcs12Content,
                        (byte) 0
                );
            }

            if (passwordBytes != null) {
                Arrays.fill(
                        passwordBytes,
                        (byte) 0
                );
            }

            if (password != null) {
                Arrays.fill(
                        password,
                        '\0'
                );
            }
        }
    }

    private static char[] decodePassword(
            byte[] passwordBytes
    ) {

        try {

            CharBuffer decoded =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(
                                    CodingErrorAction.REPORT
                            )
                            .onUnmappableCharacter(
                                    CodingErrorAction.REPORT
                            )
                            .decode(
                                    ByteBuffer.wrap(
                                            passwordBytes
                                    )
                            );

            char[] password =
                    new char[
                            decoded.remaining()
                            ];

            decoded.get(
                    password
            );

            return password;

        } catch (CharacterCodingException exception) {

            throw new SigningCertificateException(
                    "Certificate password is not valid UTF-8",
                    exception
            );
        }
    }

    private DocumentCertificateSelection mapDocumentSelection(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new DocumentCertificateSelection(

                resultSet.getObject(
                        "taxpayer_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "signing_certificate_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "active_signing_certificate_id",
                        UUID.class
                ),
                resultSet.getString("ruc")
        );
    }

    private CertificateRow mapCertificate(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new CertificateRow(

                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "taxpayer_id",
                        UUID.class
                ),

                resultSet.getString(
                        "alias"
                ),

                resultSet.getString(
                        "status"
                ),

                resultSet
                        .getTimestamp(
                                "valid_from"
                        )
                        .toInstant(),

                resultSet
                        .getTimestamp(
                                "valid_until"
                        )
                        .toInstant(),

                resultSet.getBytes(
                        "encrypted_content"
                ),

                resultSet.getBytes(
                        "encrypted_password"
                ),
                resultSet.getString("fingerprint"),
                resultSet.getString("authorized_ruc"),
                resultSet.getString("authorization_evidence_sha256"),
                resultSet.getBytes("authorization_trust_anchor")
        );
    }

    private record DocumentCertificateSelection(
            UUID taxpayerId,
            UUID signingCertificateId,
            UUID activeSigningCertificateId,
            String ruc
    ) {
    }

    private record CertificateRow(
            UUID id,
            UUID taxpayerId,
            String alias,
            String status,
            Instant validFrom,
            Instant validUntil,
            byte[] encryptedContent,
            byte[] encryptedPassword,
            String fingerprint,
            String authorizedRuc,
            String authorizationEvidence,
            byte[] trustAnchor
    ) {
    }
}
