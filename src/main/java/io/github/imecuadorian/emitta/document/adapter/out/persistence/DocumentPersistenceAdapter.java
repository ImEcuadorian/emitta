package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentPersistenceAdapter
        implements DocumentRepository {

    private final DocumentJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public DocumentPersistenceAdapter(
            DocumentJpaRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static SqlParameterValue timestamptz(
            Instant instant
    ) {

        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                instant == null
                        ? null
                        : instant.atOffset(
                        ZoneOffset.UTC
                )
        );
    }

    @Override
    public Document save(
            Document document
    ) {
        DocumentJpaEntity entity =
                DocumentPersistenceMapper.toEntity(
                        document
                );

        DocumentJpaEntity saved =
                repository.save(entity);

        return DocumentPersistenceMapper.toDomain(
                saved
        );
    }

    @Override
    public Optional<Document> findById(
            UUID id
    ) {
        return repository
                .findById(id)
                .map(
                        DocumentPersistenceMapper::toDomain
                );
    }

    @Override
    public Optional<Document>
    findByTenantIdAndIdempotencyKey(
            UUID tenantId,
            IdempotencyKey idempotencyKey
    ) {
        return repository
                .findByTenantIdAndIdempotencyKey(
                        tenantId,
                        idempotencyKey.value()
                )
                .map(
                        DocumentPersistenceMapper::toDomain
                );
    }

    @Override
    public Optional<Document> findByAccessKey(
            String accessKey
    ) {
        return repository
                .findByAccessKey(accessKey)
                .map(
                        DocumentPersistenceMapper::toDomain
                );
    }

    @Override
    public boolean insertIfAbsent(
            Document document
    ) {

        int affectedRows =
                jdbcTemplate.update(
                        """
                        INSERT INTO emitta.documents (
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
                            issued_at,
                            received_at,
                            queued_at,
                            processing_started_at,
                            signed_at,
                            submitted_at,
                            authorized_at,
                            failed_at,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?,
                            ?, ?, ?, ?, ?,
                            ?, ?, ?, ?, ?,
                            ?, ?, ?, ?
                        )
                        ON CONFLICT ON CONSTRAINT
                            uq_documents_tenant_idempotency
                        DO NOTHING
                        """,
                        document.getId(),
                        document.getTenantId(),
                        document.getTaxpayerId(),
                        document.getPointOfIssueId(),
                        document.getDocumentType().name(),
                        document.getEnvironment().name(),
                        document.getSequential(),
                        document.getAccessKey(),
                        document.getStatus().name(),
                        document.getIdempotencyKey().value(),

                        timestamptz(
                                document.getIssuedAt()
                        ),

                        timestamptz(
                                document.getReceivedAt()
                        ),

                        timestamptz(
                                document.getQueuedAt()
                        ),

                        timestamptz(
                                document.getProcessingStartedAt()
                        ),

                        timestamptz(
                                document.getSignedAt()
                        ),

                        timestamptz(
                                document.getSubmittedAt()
                        ),

                        timestamptz(
                                document.getAuthorizedAt()
                        ),

                        timestamptz(
                                document.getFailedAt()
                        ),

                        timestamptz(
                                document.getCreatedAt()
                        ),

                        timestamptz(
                                document.getUpdatedAt()
                        )
                );

        return affectedRows == 1;
    }

    @Override
    public Optional<Document> findByIdForUpdate(
            UUID id
    ) {
        return repository
                .findByIdForUpdate(id)
                .map(
                        DocumentPersistenceMapper::toDomain
                );
    }
}