package io.github.imecuadorian.emitta.document.application.port.out;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<Document> findByTenantIdAndIdempotencyKey(
            UUID tenantId,
            IdempotencyKey idempotencyKey
    );

    Optional<Document> findByAccessKey(
            String accessKey
    );
}