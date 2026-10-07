package io.github.imecuadorian.emitta.documentartifact.adapter.out.persistence;

import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactRepositoryPort;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgreSqlDocumentArtifactRepositoryAdapter
        implements DocumentArtifactRepositoryPort {

    private static final String FIND_SQL = """
            SELECT
                id,
                document_id,
                artifact_type,
                content_type,
                storage_key,
                sha256,
                size_bytes,
                created_at

            FROM document_artifacts

            WHERE document_id = :documentId
              AND artifact_type = :artifactType
            """;

    private static final String INSERT_SQL = """
            INSERT INTO document_artifacts (
                id,
                document_id,
                artifact_type,
                content_type,
                storage_key,
                sha256,
                size_bytes,
                created_at
            )
            VALUES (
                :id,
                :documentId,
                :artifactType,
                :contentType,
                :storageKey,
                :sha256,
                :sizeBytes,
                :createdAt
            )

            RETURNING
                id,
                document_id,
                artifact_type,
                content_type,
                storage_key,
                sha256,
                size_bytes,
                created_at
            """;

    private final JdbcClient jdbcClient;

    public PostgreSqlDocumentArtifactRepositoryAdapter(
            JdbcClient jdbcClient
    ) {

        this.jdbcClient =
                Objects.requireNonNull(
                        jdbcClient
                );
    }

    @Override
    public Optional<DocumentArtifact>
    findByDocumentIdAndType(
            UUID documentId,
            DocumentArtifactType type
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                type,
                "Artifact type cannot be null"
        );

        return jdbcClient
                .sql(
                        FIND_SQL
                )
                .param(
                        "documentId",
                        documentId
                )
                .param(
                        "artifactType",
                        type.name()
                )
                .query(
                        this::mapArtifact
                )
                .optional();
    }

    @Override
    public DocumentArtifact save(
            DocumentArtifact artifact
    ) {

        Objects.requireNonNull(
                artifact,
                "Artifact cannot be null"
        );

        return jdbcClient
                .sql(
                        INSERT_SQL
                )
                .param(
                        "id",
                        artifact.id()
                )
                .param(
                        "documentId",
                        artifact.documentId()
                )
                .param(
                        "artifactType",
                        artifact.type()
                                .name()
                )
                .param(
                        "contentType",
                        artifact.contentType()
                )
                .param(
                        "storageKey",
                        artifact.storageKey()
                )
                .param(
                        "sha256",
                        artifact.sha256()
                )
                .param(
                        "sizeBytes",
                        artifact.sizeBytes()
                )
                .param(
                        "createdAt",
                        Timestamp.from(artifact.createdAt())
                )
                .query(
                        this::mapArtifact
                )
                .single();
    }

    private DocumentArtifact mapArtifact(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new DocumentArtifact(

                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "document_id",
                        UUID.class
                ),

                DocumentArtifactType.valueOf(
                        resultSet.getString(
                                "artifact_type"
                        )
                ),

                resultSet.getString(
                        "content_type"
                ),

                resultSet.getString(
                        "storage_key"
                ),

                resultSet.getString(
                        "sha256"
                ),

                resultSet.getLong(
                        "size_bytes"
                ),

                resultSet
                        .getTimestamp(
                                "created_at"
                        )
                        .toInstant()
        );
    }
}