package io.github.imecuadorian.emitta.pointofissue.adapter.out.persistence;

import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "points_of_issue",
        schema = "emitta"
)
class PointOfIssueJpaEntity {

    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "establishment_id",
            nullable = false,
            updatable = false
    )
    private UUID establishmentId;

    @Column(
            name = "code",
            nullable = false,
            length = 3,
            updatable = false
    )
    private String code;

    @Column(
            name = "name",
            length = 200
    )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private PointOfIssueStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected PointOfIssueJpaEntity() {
    }

    PointOfIssueJpaEntity(
            UUID id,
            UUID establishmentId,
            String code,
            String name,
            PointOfIssueStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.establishmentId = establishmentId;
        this.code = code;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getEstablishmentId() {
        return establishmentId;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    PointOfIssueStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}