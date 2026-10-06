package io.github.imecuadorian.emitta.pointofissue.adapter.out.persistence;

import io.github.imecuadorian.emitta.pointofissue.application.port.out.PointOfIssueRepository;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PointOfIssuePersistenceAdapter
        implements PointOfIssueRepository {

    private final PointOfIssueJpaRepository repository;

    public PointOfIssuePersistenceAdapter(
            PointOfIssueJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public PointOfIssue save(
            PointOfIssue pointOfIssue
    ) {
        PointOfIssueJpaEntity entity =
                PointOfIssuePersistenceMapper.toEntity(
                        pointOfIssue
                );

        PointOfIssueJpaEntity saved =
                repository.save(entity);

        return PointOfIssuePersistenceMapper.toDomain(
                saved
        );
    }

    @Override
    public Optional<PointOfIssue> findById(
            UUID id
    ) {
        return repository
                .findById(id)
                .map(
                        PointOfIssuePersistenceMapper::toDomain
                );
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public boolean existsByEstablishmentIdAndCode(
            UUID establishmentId,
            PointOfIssueCode code
    ) {
        return repository
                .existsByEstablishmentIdAndCode(
                        establishmentId,
                        code.value()
                );
    }
}