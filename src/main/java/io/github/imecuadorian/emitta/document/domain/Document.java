package io.github.imecuadorian.emitta.document.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class Document {

    private final UUID id;

    private final UUID tenantId;
    private final UUID taxpayerId;
    private final UUID pointOfIssueId;

    private final DocumentType documentType;
    private final FiscalEnvironment environment;

    private Long sequential;
    private String accessKey;

    private DocumentStatus status;

    private final IdempotencyKey idempotencyKey;

    private final Instant issuedAt;
    private final Instant receivedAt;

    private Instant queuedAt;
    private Instant processingStartedAt;
    private Instant signedAt;
    private Instant submittedAt;
    private Instant authorizedAt;
    private Instant failedAt;

    private final Instant createdAt;
    private Instant updatedAt;

    private Document(
            UUID id,
            UUID tenantId,
            UUID taxpayerId,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            Long sequential,
            String accessKey,
            DocumentStatus status,
            IdempotencyKey idempotencyKey,
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
        this.id = Objects.requireNonNull(
                id,
                "Document id cannot be null"
        );

        this.tenantId = Objects.requireNonNull(
                tenantId,
                "Tenant id cannot be null"
        );

        this.taxpayerId = Objects.requireNonNull(
                taxpayerId,
                "Taxpayer id cannot be null"
        );

        this.pointOfIssueId = Objects.requireNonNull(
                pointOfIssueId,
                "Point of issue id cannot be null"
        );

        this.documentType = Objects.requireNonNull(
                documentType,
                "Document type cannot be null"
        );

        this.environment = Objects.requireNonNull(
                environment,
                "Fiscal environment cannot be null"
        );

        validateSequential(sequential);
        validateAccessKey(accessKey);

        this.sequential = sequential;
        this.accessKey = accessKey;

        this.status = Objects.requireNonNull(
                status,
                "Document status cannot be null"
        );

        this.idempotencyKey = Objects.requireNonNull(
                idempotencyKey,
                "Idempotency key cannot be null"
        );

        this.issuedAt = Objects.requireNonNull(
                issuedAt,
                "Issued at cannot be null"
        );

        this.receivedAt = Objects.requireNonNull(
                receivedAt,
                "Received at cannot be null"
        );

        this.queuedAt = queuedAt;
        this.processingStartedAt = processingStartedAt;
        this.signedAt = signedAt;
        this.submittedAt = submittedAt;
        this.authorizedAt = authorizedAt;
        this.failedAt = failedAt;

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Created at cannot be null"
        );

        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Updated at cannot be null"
        );
    }

    public static Document create(
            UUID id,
            UUID tenantId,
            UUID taxpayerId,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            IdempotencyKey idempotencyKey,
            Instant issuedAt,
            Instant now
    ) {
        Objects.requireNonNull(
                now,
                "Creation time cannot be null"
        );

        return new Document(
                id,
                tenantId,
                taxpayerId,
                pointOfIssueId,
                documentType,
                environment,
                null,
                null,
                DocumentStatus.RECEIVED,
                idempotencyKey,
                issuedAt,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    public static Document restore(
            UUID id,
            UUID tenantId,
            UUID taxpayerId,
            UUID pointOfIssueId,
            DocumentType documentType,
            FiscalEnvironment environment,
            Long sequential,
            String accessKey,
            DocumentStatus status,
            IdempotencyKey idempotencyKey,
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
        return new Document(
                id,
                tenantId,
                taxpayerId,
                pointOfIssueId,
                documentType,
                environment,
                sequential,
                accessKey,
                status,
                idempotencyKey,
                issuedAt,
                receivedAt,
                queuedAt,
                processingStartedAt,
                signedAt,
                submittedAt,
                authorizedAt,
                failedAt,
                createdAt,
                updatedAt
        );
    }

    public void assignFiscalIdentity(
            long sequential,
            String accessKey,
            Instant now
    ) {
        if (
                this.sequential != null
                        || this.accessKey != null
        ) {
            throw new IllegalStateException(
                    "Fiscal identity has already been assigned"
            );
        }

        validateSequential(sequential);
        validateAccessKey(accessKey);

        this.sequential = sequential;
        this.accessKey = accessKey;

        touch(now);
    }

    public void queue(Instant now) {
        requireStatus(DocumentStatus.RECEIVED);

        this.status = DocumentStatus.QUEUED;
        this.queuedAt = requireTime(now);

        touch(now);
    }

    public void startGenerating(Instant now) {
        requireStatus(
                DocumentStatus.QUEUED,
                DocumentStatus.RETRY_PENDING
        );

        this.status = DocumentStatus.GENERATING;
        this.processingStartedAt = requireTime(now);

        touch(now);
    }

    public void markSigned(Instant now) {
        requireStatus(DocumentStatus.GENERATING);

        this.status = DocumentStatus.SIGNED;
        this.signedAt = requireTime(now);

        touch(now);
    }

    public void markSubmitted(Instant now) {
        requireStatus(DocumentStatus.SIGNED);

        this.status = DocumentStatus.SUBMITTED;
        this.submittedAt = requireTime(now);

        touch(now);
    }

    public void markAuthorized(Instant now) {
        requireStatus(DocumentStatus.SUBMITTED);

        this.status = DocumentStatus.AUTHORIZED;
        this.authorizedAt = requireTime(now);

        touch(now);
    }

    public void markRejected(Instant now) {
        requireStatus(DocumentStatus.SUBMITTED);

        this.status = DocumentStatus.REJECTED;

        touch(now);
    }

    public void scheduleRetry(Instant now) {
        requireStatus(
                DocumentStatus.SUBMITTED,
                DocumentStatus.REJECTED
        );

        this.status = DocumentStatus.RETRY_PENDING;

        touch(now);
    }

    public void markFailed(Instant now) {
        if (
                status == DocumentStatus.AUTHORIZED
                        || status == DocumentStatus.FAILED
        ) {
            throw new IllegalStateException(
                    "Cannot fail document from status " + status
            );
        }

        this.status = DocumentStatus.FAILED;
        this.failedAt = requireTime(now);

        touch(now);
    }

    private void requireStatus(
            DocumentStatus... allowed
    ) {
        for (DocumentStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }

        throw new IllegalStateException(
                "Invalid document transition from status "
                        + status
        );
    }

    private static void validateSequential(
            Long sequential
    ) {
        if (sequential == null) {
            return;
        }

        if (
                sequential < 1
                        || sequential > 999_999_999L
        ) {
            throw new IllegalArgumentException(
                    "Sequential must be between 1 and 999999999"
            );
        }
    }

    private static void validateAccessKey(
            String accessKey
    ) {
        if (accessKey == null) {
            return;
        }

        if (!accessKey.matches("\\d{49}")) {
            throw new IllegalArgumentException(
                    "Access key must contain exactly 49 numeric digits"
            );
        }
    }

    private static Instant requireTime(
            Instant now
    ) {
        return Objects.requireNonNull(
                now,
                "Transition time cannot be null"
        );
    }

    private void touch(Instant now) {
        this.updatedAt = requireTime(now);
    }

}