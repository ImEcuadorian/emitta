package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentPersistenceAdapter
        implements DocumentRepository {

    private final DocumentJpaRepository repository;

    public DocumentPersistenceAdapter(
            DocumentJpaRepository repository
    ) {
        this.repository = repository;
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
}