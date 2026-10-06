package io.github.imecuadorian.emitta.establishment.adapter.out.persistence;

import io.github.imecuadorian.emitta.establishment.domain.EstablishmentStatus;
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
        name = "establishments",
        schema = "emitta"
)
class EstablishmentJpaEntity {

    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "taxpayer_id",
            nullable = false,
            updatable = false
    )
    private UUID taxpayerId;

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

    @Column(
            name = "address",
            length = 500
    )
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private EstablishmentStatus status;

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

    protected EstablishmentJpaEntity() {
    }

    EstablishmentJpaEntity(
            UUID id,
            UUID taxpayerId,
            String code,
            String name,
            String address,
            EstablishmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.taxpayerId = taxpayerId;
        this.code = code;
        this.name = name;
        this.address = address;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTaxpayerId() {
        return taxpayerId;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getAddress() {
        return address;
    }

    EstablishmentStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}