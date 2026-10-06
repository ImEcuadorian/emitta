package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;

final class DocumentPersistenceMapper {

    private DocumentPersistenceMapper() {
    }

    static DocumentJpaEntity toEntity(
            Document document
    ) {
        return new DocumentJpaEntity(
                document.getId(),
                document.getTenantId(),
                document.getTaxpayerId(),
                document.getPointOfIssueId(),
                document.getDocumentType(),
                document.getEnvironment(),
                document.getSequential(),
                document.getAccessKey(),
                document.getStatus(),
                document.getIdempotencyKey().value(),
                document.getIssuedAt(),
                document.getReceivedAt(),
                document.getQueuedAt(),
                document.getProcessingStartedAt(),
                document.getSignedAt(),
                document.getSubmittedAt(),
                document.getAuthorizedAt(),
                document.getFailedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    static Document toDomain(
            DocumentJpaEntity entity
    ) {
        return Document.restore(
                entity.getId(),
                entity.getTenantId(),
                entity.getTaxpayerId(),
                entity.getPointOfIssueId(),
                entity.getDocumentType(),
                entity.getEnvironment(),
                entity.getSequential(),
                entity.getAccessKey(),
                entity.getStatus(),
                new IdempotencyKey(
                        entity.getIdempotencyKey()
                ),
                entity.getIssuedAt(),
                entity.getReceivedAt(),
                entity.getQueuedAt(),
                entity.getProcessingStartedAt(),
                entity.getSignedAt(),
                entity.getSubmittedAt(),
                entity.getAuthorizedAt(),
                entity.getFailedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}