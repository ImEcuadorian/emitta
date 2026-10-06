package io.github.imecuadorian.emitta.document.adapter.out.persistence;

import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
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
        name = "documents",
        schema = "emitta"
)
class DocumentJpaEntity {

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
            name = "taxpayer_id",
            nullable = false,
            updatable = false
    )
    private UUID taxpayerId;

    @Column(
            name = "point_of_issue_id",
            nullable = false,
            updatable = false
    )
    private UUID pointOfIssueId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "document_type",
            nullable = false,
            length = 40,
            updatable = false
    )
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "environment",
            nullable = false,
            length = 20,
            updatable = false
    )
    private FiscalEnvironment environment;

    @Column(
            name = "sequential"
    )
    private Long sequential;

    @Column(
            name = "access_key",
            length = 49
    )
    private String accessKey;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private DocumentStatus status;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 255,
            updatable = false
    )
    private String idempotencyKey;

    @Column(
            name = "issued_at",
            nullable = false,
            updatable = false
    )
    private Instant issuedAt;

    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private Instant receivedAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

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

    protected DocumentJpaEntity() {
    }

    DocumentJpaEntity(
            UUID id,
            UUID tenantId,
            UUID taxpayerId,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            Long sequential,
            String accessKey,
            DocumentStatus status,
            String idempotencyKey,
            Instant issuedAt,
            Instant receivedAt,
            Instant queuedAt,
            Instant processingStartedAt,
            Instant signedAt,
            Instant submittedAt,
            Instant authorizedAt,
            Instant failedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.taxpayerId = taxpayerId;
        this.pointOfIssueId = pointOfIssueId;
        this.documentType = documentType;
        this.environment = environment;
        this.sequential = sequential;
        this.accessKey = accessKey;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.issuedAt = issuedAt;
        this.receivedAt = receivedAt;
        this.queuedAt = queuedAt;
        this.processingStartedAt = processingStartedAt;
        this.signedAt = signedAt;
        this.submittedAt = submittedAt;
        this.authorizedAt = authorizedAt;
        this.failedAt = failedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getTaxpayerId() {
        return taxpayerId;
    }

    UUID getPointOfIssueId() {
        return pointOfIssueId;
    }

    DocumentType getDocumentType() {
        return documentType;
    }

    FiscalEnvironment getEnvironment() {
        return environment;
    }

    Long getSequential() {
        return sequential;
    }

    String getAccessKey() {
        return accessKey;
    }

    DocumentStatus getStatus() {
        return status;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    Instant getReceivedAt() {
        return receivedAt;
    }

    Instant getQueuedAt() {
        return queuedAt;
    }

    Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    Instant getSignedAt() {
        return signedAt;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }

    Instant getAuthorizedAt() {
        return authorizedAt;
    }

    Instant getFailedAt() {
        return failedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}