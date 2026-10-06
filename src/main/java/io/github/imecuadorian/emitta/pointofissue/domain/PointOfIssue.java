package io.github.imecuadorian.emitta.pointofissue.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PointOfIssue {

    private final UUID id;
    private final UUID establishmentId;
    private final PointOfIssueCode code;

    private String name;
    private PointOfIssueStatus status;

    private final Instant createdAt;
    private Instant updatedAt;

    private PointOfIssue(
            UUID id,
            UUID establishmentId,
            PointOfIssueCode code,
            String name,
            PointOfIssueStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(
                id,
                "Point of issue id cannot be null"
        );

        this.establishmentId = Objects.requireNonNull(
                establishmentId,
                "Establishment id cannot be null"
        );

        this.code = Objects.requireNonNull(
                code,
                "Point of issue code cannot be null"
        );

        this.name = normalizeOptionalName(name);

        this.status = Objects.requireNonNull(
                status,
                "Point of issue status cannot be null"
        );

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Creation time cannot be null"
        );

        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Update time cannot be null"
        );
    }

    public static PointOfIssue create(
            UUID id,
            UUID establishmentId,
            PointOfIssueCode code,
            String name,
            Instant now
    ) {
        Objects.requireNonNull(
                now,
                "Creation time cannot be null"
        );

        return new PointOfIssue(
                id,
                establishmentId,
                code,
                name,
                PointOfIssueStatus.ACTIVE,
                now,
                now
        );
    }

    public static PointOfIssue restore(
            UUID id,
            UUID establishmentId,
            PointOfIssueCode code,
            String name,
            PointOfIssueStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PointOfIssue(
                id,
                establishmentId,
                code,
                name,
                status,
                createdAt,
                updatedAt
        );
    }

    public void rename(
            String name,
            Instant now
    ) {
        this.name = normalizeOptionalName(name);
        touch(now);
    }

    public void activate(Instant now) {
        this.status = PointOfIssueStatus.ACTIVE;
        touch(now);
    }

    public void deactivate(Instant now) {
        this.status = PointOfIssueStatus.INACTIVE;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(
                now,
                "Update time cannot be null"
        );
    }

    private static String normalizeOptionalName(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > 200) {
            throw new IllegalArgumentException(
                    "Point of issue name cannot exceed 200 characters"
            );
        }

        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public PointOfIssueCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PointOfIssueStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}