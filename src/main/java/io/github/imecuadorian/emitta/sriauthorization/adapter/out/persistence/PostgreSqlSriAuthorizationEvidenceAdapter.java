package io.github.imecuadorian.emitta.sriauthorization.adapter.out.persistence;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationMessage;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PostgreSqlSriAuthorizationEvidenceAdapter
        implements SriAuthorizationEvidencePort {

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    public PostgreSqlSriAuthorizationEvidenceAdapter(
            JdbcClient jdbcClient,
            TransactionTemplate transactionTemplate
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.transactionTemplate = Objects.requireNonNull(
                transactionTemplate
        );
    }

    @Override
    public SriAuthorizationEvidence save(
            SriAuthorizationEvidence evidence
    ) {

        Objects.requireNonNull(evidence);

        return Objects.requireNonNull(
                transactionTemplate.execute(status -> {

                    DocumentFiscalIdentity document =
                            jdbcClient.sql("""
                SELECT status, access_key, environment
                FROM emitta.documents
                WHERE id = :documentId
                FOR UPDATE
                """)
                                    .param("documentId", evidence.documentId())
                                    .query((rs, rowNum) ->
                                            new DocumentFiscalIdentity(
                                                    rs.getString("status"),
                                                    rs.getString("access_key"),
                                                    rs.getString("environment")
                                            )
                                    )
                                    .optional()
                                    .orElseThrow(
                                            () -> new IllegalStateException(
                                                    "Document not found: "
                                                            + evidence.documentId()
                                            )
                                    );

                    if (!List.of(
                            "SUBMITTED",
                            "RETRY_PENDING",
                            "AUTHORIZED"
                    ).contains(document.status())) {

                        throw new IllegalStateException(
                                "Cannot persist SRI authorization from document status "
                                        + document.status()
                        );
                    }

                    if (!evidence.authorizationNumber().equals(document.accessKey())) {

                        throw new IllegalStateException(
                                "SRI authorization number does not match document access key"
                        );
                    }

                    if (!evidence.environment().name().equals(document.environment())) {

                        throw new IllegalStateException(
                                "SRI authorization environment does not match document"
                        );
                    }


                    String storedSha256 = jdbcClient.sql("""
                            SELECT sha256
                            FROM emitta.document_artifacts
                            WHERE document_id = :documentId
                              AND artifact_type = 'AUTHORIZED_XML'
                            """)
                            .param("documentId", evidence.documentId())
                            .query(String.class)
                            .optional()
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "AUTHORIZED_XML artifact metadata is missing"
                                    )
                            );

                    if (!storedSha256.equals(
                            evidence.authorizedXmlSha256()
                    )) {

                        throw new IllegalStateException(
                                "SRI authorization evidence SHA-256 mismatch"
                        );
                    }

                    Optional<SriAuthorizationEvidence> existing =
                            findByDocumentId(evidence.documentId());

                    if (existing.isPresent()) {

                        if (!existing.orElseThrow().equals(evidence)) {

                            throw new IllegalStateException(
                                    "Conflicting SRI authorization evidence"
                            );
                        }

                        return existing.orElseThrow();
                    }

                    jdbcClient.sql("""
                            INSERT INTO emitta.sri_authorizations (
                                document_id,
                                authorization_number,
                                authorized_at,
                                environment,
                                authorized_xml_sha256
                            )
                            VALUES (
                                :documentId,
                                :authorizationNumber,
                                :authorizedAt,
                                :environment,
                                :sha256
                            )
                            """)
                            .param("documentId", evidence.documentId())
                            .param(
                                    "authorizationNumber",
                                    evidence.authorizationNumber()
                            )
                            .param(
                                    "authorizedAt",
                                    evidence.authorizedAt()
                                            .atOffset(ZoneOffset.UTC)
                            )
                            .param(
                                    "environment",
                                    evidence.environment().name()
                            )
                            .param(
                                    "sha256",
                                    evidence.authorizedXmlSha256()
                            )
                            .update();

                    int ordinal = 1;

                    for (SriAuthorizationMessage message :
                            evidence.messages()) {

                        jdbcClient.sql("""
                                INSERT INTO emitta.sri_authorization_messages (
                                    document_id,
                                    ordinal,
                                    identifier,
                                    message,
                                    additional_information,
                                    type
                                )
                                VALUES (
                                    :documentId,
                                    :ordinal,
                                    :identifier,
                                    :message,
                                    :additionalInformation,
                                    :type
                                )
                                """)
                                .param("documentId", evidence.documentId())
                                .param("ordinal", ordinal++)
                                .param("identifier", message.identifier())
                                .param("message", message.message())
                                .param(
                                        "additionalInformation",
                                        message.additionalInformation(),
                                        Types.VARCHAR
                                )
                                .param("type", message.type())
                                .update();
                    }

                    return evidence;
                }),
                "SRI authorization transaction returned null"
        );
    }

    @Override
    public Optional<SriAuthorizationEvidence> findByDocumentId(
            UUID documentId
    ) {

        Objects.requireNonNull(documentId);

        return jdbcClient.sql("""
                        SELECT
                            document_id,
                            authorization_number,
                            authorized_at,
                            environment,
                            authorized_xml_sha256
                        FROM emitta.sri_authorizations
                        WHERE document_id = :documentId
                        """)
                .param("documentId", documentId)
                .query((rs, rowNum) ->
                        new SriAuthorizationEvidence(
                                rs.getObject(
                                        "document_id",
                                        UUID.class
                                ),
                                rs.getString("authorization_number"),
                                rs.getObject(
                                        "authorized_at",
                                        OffsetDateTime.class
                                ).toInstant(),
                                FiscalEnvironment.valueOf(
                                        rs.getString("environment")
                                ),
                                rs.getString("authorized_xml_sha256"),
                                loadMessages(documentId)
                        )
                )
                .optional();
    }

    private List<SriAuthorizationMessage> loadMessages(
            UUID documentId
    ) {

        return jdbcClient.sql("""
                        SELECT
                            identifier,
                            message,
                            additional_information,
                            type
                        FROM emitta.sri_authorization_messages
                        WHERE document_id = :documentId
                        ORDER BY ordinal
                        """)
                .param("documentId", documentId)
                .query((rs, rowNum) ->
                        new SriAuthorizationMessage(
                                rs.getString("identifier"),
                                rs.getString("message"),
                                rs.getString("additional_information"),
                                rs.getString("type")
                        )
                )
                .list();
    }

    private record DocumentFiscalIdentity(
            String status,
            String accessKey,
            String environment
    ) {
    }
}