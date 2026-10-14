package io.github.imecuadorian.emitta.document.application.port.out;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    record AdmissionDocument(UUID id, UUID tenantId, io.github.imecuadorian.emitta.document.domain.DocumentStatus status) {}

    default java.util.Map<UUID, AdmissionDocument> findForAdmission(java.util.Collection<UUID> ids) {
        var result = new java.util.HashMap<UUID, AdmissionDocument>();
        for (var id : ids) findById(id).ifPresent(d -> result.put(id, new AdmissionDocument(id, d.getTenantId(), d.getStatus())));
        return result;
    }

    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<Document> findByTenantIdAndIdempotencyKey(
            UUID tenantId,
            IdempotencyKey idempotencyKey
    );

    Optional<Document> findByAccessKey(
            String accessKey
    );

    boolean insertIfAbsent(Document document);

    Optional<Document> findByIdForUpdate(UUID id);
}
