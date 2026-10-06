package io.github.imecuadorian.emitta.pointofissue.adapter.out.persistence;

import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;

final class PointOfIssuePersistenceMapper {

    private PointOfIssuePersistenceMapper() {
    }

    static PointOfIssueJpaEntity toEntity(
            PointOfIssue pointOfIssue
    ) {
        return new PointOfIssueJpaEntity(
                pointOfIssue.getId(),
                pointOfIssue.getEstablishmentId(),
                pointOfIssue.getCode().value(),
                pointOfIssue.getName(),
                pointOfIssue.getStatus(),
                pointOfIssue.getCreatedAt(),
                pointOfIssue.getUpdatedAt()
        );
    }

    static PointOfIssue toDomain(
            PointOfIssueJpaEntity entity
    ) {
        return PointOfIssue.restore(
                entity.getId(),
                entity.getEstablishmentId(),
                new PointOfIssueCode(
                        entity.getCode()
                ),
                entity.getName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}