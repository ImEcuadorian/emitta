package io.github.imecuadorian.emitta.tenant.domain;

import lombok.Getter;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class Tenant {

    private final UUID id;

    private String name;
    private TenantStatus status;

    private final Instant createdAt;
    private Instant updatedAt;

    private Tenant(
            UUID id,
            String name,
            TenantStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(
                id,
                "Tenant id cannot be null"
        );

        this.name = validateName(name);

        this.status = Objects.requireNonNull(
                status,
                "Tenant status cannot be null"
        );

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Tenant creation date cannot be null"
        );

        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Tenant update date cannot be null"
        );
    }

    public static @NonNull Tenant create(
            UUID id,
            String name,
            Instant now
    ) {
        Objects.requireNonNull(
                now,
                "Creation time cannot be null"
        );

        return new Tenant(
                id,
                name,
                TenantStatus.ACTIVE,
                now,
                now
        );
    }

    public static @NonNull Tenant restore(
            UUID id,
            String name,
            TenantStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Tenant(
                id,
                name,
                status,
                createdAt,
                updatedAt
        );
    }

    public void rename(
            String newName,
            Instant now
    ) {
        this.name = validateName(newName);
        touch(now);
    }

    public void activate(Instant now) {
        this.status = TenantStatus.ACTIVE;
        touch(now);
    }

    public void suspend(Instant now) {
        this.status = TenantStatus.SUSPENDED;
        touch(now);
    }

    public void disable(Instant now) {
        this.status = TenantStatus.DISABLED;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(
                now,
                "Update time cannot be null"
        );
    }

    private static @NonNull String validateName(String name) {
        Objects.requireNonNull(
                name,
                "Tenant name cannot be null"
        );

        String normalized = name.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tenant name cannot be blank"
            );
        }

        if (normalized.length() > 150) {
            throw new IllegalArgumentException(
                    "Tenant name cannot exceed 150 characters"
            );
        }

        return normalized;
    }

}