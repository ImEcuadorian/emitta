package io.github.imecuadorian.emitta.taxpayer.adapter.out.persistence;

import io.github.imecuadorian.emitta.taxpayer.domain.TaxpayerStatus;
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
        name = "taxpayers",
        schema = "emitta"
)
class TaxpayerJpaEntity {

    @Id
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "tenant_id",
            nullable = false,
            updatable = false
    )
    private UUID tenantId;

    @Column(
            name = "ruc",
            nullable = false,
            length = 13,
            updatable = false
    )
    private String ruc;

    @Column(
            name = "legal_name",
            nullable = false,
            length = 300
    )
    private String legalName;

    @Column(
            name = "trade_name",
            length = 300
    )
    private String tradeName;

    @Column(
            name = "main_address",
            nullable = false,
            length = 300
    )
    private String mainAddress;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private TaxpayerStatus status;

    @Column(
            name = "test_enabled",
            nullable = false
    )
    private boolean testEnabled;

    @Column(
            name = "production_enabled",
            nullable = false
    )
    private boolean productionEnabled;

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

    protected TaxpayerJpaEntity() {
    }

    TaxpayerJpaEntity(
            UUID id,
            UUID tenantId,
            String ruc,
            String legalName,
            String tradeName,
            String mainAddress,
            TaxpayerStatus status,
            boolean testEnabled,
            boolean productionEnabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.ruc = ruc;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.mainAddress = mainAddress;
        this.status = status;
        this.testEnabled = testEnabled;
        this.productionEnabled = productionEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getRuc() {
        return ruc;
    }

    String getLegalName() {
        return legalName;
    }

    String getTradeName() {
        return tradeName;
    }

    String getMainAddress() {
        return mainAddress;
    }

    TaxpayerStatus getStatus() {
        return status;
    }

    boolean isTestEnabled() {
        return testEnabled;
    }

    boolean isProductionEnabled() {
        return productionEnabled;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}