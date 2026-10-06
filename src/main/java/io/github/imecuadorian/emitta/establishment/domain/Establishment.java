package io.github.imecuadorian.emitta.establishment.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class Establishment {

    private final UUID id;
    private final UUID taxpayerId;
    private final EstablishmentCode code;

    private String name;
    private String address;
    private EstablishmentStatus status;

    private final Instant createdAt;
    private Instant updatedAt;

    private Establishment(
            UUID id,
            UUID taxpayerId,
            EstablishmentCode code,
            String name,
            String address,
            EstablishmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(
                id,
                "Establishment id cannot be null"
        );

        this.taxpayerId = Objects.requireNonNull(
                taxpayerId,
                "Taxpayer id cannot be null"
        );

        this.code = Objects.requireNonNull(
                code,
                "Establishment code cannot be null"
        );

        this.name = normalizeOptionalText(
                name,
                200,
                "Establishment name"
        );

        this.address = normalizeOptionalText(
                address,
                500,
                "Establishment address"
        );

        this.status = Objects.requireNonNull(
                status,
                "Establishment status cannot be null"
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

    public static Establishment create(
            UUID id,
            UUID taxpayerId,
            EstablishmentCode code,
            String name,
            String address,
            Instant now
    ) {
        Objects.requireNonNull(
                now,
                "Creation time cannot be null"
        );

        return new Establishment(
                id,
                taxpayerId,
                code,
                name,
                address,
                EstablishmentStatus.ACTIVE,
                now,
                now
        );
    }

    public static Establishment restore(
            UUID id,
            UUID taxpayerId,
            EstablishmentCode code,
            String name,
            String address,
            EstablishmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Establishment(
                id,
                taxpayerId,
                code,
                name,
                address,
                status,
                createdAt,
                updatedAt
        );
    }

    public void updateDetails(
            String name,
            String address,
            Instant now
    ) {
        this.name = normalizeOptionalText(
                name,
                200,
                "Establishment name"
        );

        this.address = normalizeOptionalText(
                address,
                500,
                "Establishment address"
        );

        touch(now);
    }

    public void activate(Instant now) {
        this.status = EstablishmentStatus.ACTIVE;
        touch(now);
    }

    public void deactivate(Instant now) {
        this.status = EstablishmentStatus.INACTIVE;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(
                now,
                "Update time cannot be null"
        );
    }

    private static String normalizeOptionalText(
            String value,
            int maxLength,
            String field
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " cannot exceed "
                            + maxLength
                            + " characters"
            );
        }

        return normalized;
    }

}