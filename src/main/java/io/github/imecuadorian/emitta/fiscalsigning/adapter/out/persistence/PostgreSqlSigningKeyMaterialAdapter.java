package io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.AesGcmSecretCipher;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.CertificateSecretAssociatedData;
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
                encrypted_password

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

        if (
                selection.signingCertificateId()
                        == null
        ) {

            jdbcClient
                    .sql(
                            ASSIGN_CERTIFICATE_SQL
                    )
                    .param(
                            "certificateId",
                            certificateId
                    )
                    .param(
                            "documentId",
                            documentId
                    )
                    .update();
        }

        SigningKeyMaterial keyMaterial =
                decryptKeyMaterial(
                        certificate
                );

        return new ResolvedSigningKeyMaterial(
                certificate.id(),
                keyMaterial
        );
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
                )
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
                )
        );
    }

    private record DocumentCertificateSelection(
            UUID taxpayerId,
            UUID signingCertificateId,
            UUID activeSigningCertificateId
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
            byte[] encryptedPassword
    ) {
    }
}