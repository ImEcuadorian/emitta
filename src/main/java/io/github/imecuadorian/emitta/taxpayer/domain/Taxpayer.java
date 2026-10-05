package io.github.imecuadorian.emitta.taxpayer.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class Taxpayer {

    private final UUID id;
    private final UUID tenantId;
    private final Ruc ruc;

    private String legalName;
    private String tradeName;
    private String mainAddress;

    private TaxpayerStatus status;

    private boolean testEnabled;
    private boolean productionEnabled;

    private final Instant createdAt;
    private Instant updatedAt;

    private Taxpayer(
            UUID id,
            UUID tenantId,
            Ruc ruc,
            String legalName,
            String tradeName,
            String mainAddress,
            TaxpayerStatus status,
            boolean testEnabled,
            boolean productionEnabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(
                id,
                "Taxpayer id cannot be null"
        );

        this.tenantId = Objects.requireNonNull(
                tenantId,
                "Tenant id cannot be null"
        );

        this.ruc = Objects.requireNonNull(
                ruc,
                "RUC cannot be null"
        );

        this.legalName =
                validateRequiredText(
                        legalName,
                        "Legal name"
                );

        this.tradeName =
                normalizeOptionalText(tradeName);

        this.mainAddress =
                validateRequiredText(
                        mainAddress,
                        "Main address"
                );

        this.status = Objects.requireNonNull(
                status,
                "Taxpayer status cannot be null"
        );

        this.testEnabled = testEnabled;
        this.productionEnabled = productionEnabled;

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Creation time cannot be null"
        );

        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Update time cannot be null"
        );
    }

    public static Taxpayer create(
            UUID id,
            UUID tenantId,
            Ruc ruc,
            String legalName,
            String tradeName,
            String mainAddress,
            Instant now
    ) {
        Objects.requireNonNull(
                now,
                "Creation time cannot be null"
        );

        return new Taxpayer(
                id,
                tenantId,
                ruc,
                legalName,
                tradeName,
                mainAddress,
                TaxpayerStatus.ACTIVE,
                true,
                false,
                now,
                now
        );
    }

    public static Taxpayer restore(
            UUID id,
            UUID tenantId,
            Ruc ruc,
            String legalName,
            String tradeName,
            String mainAddress,
            TaxpayerStatus status,
            boolean testEnabled,
            boolean productionEnabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Taxpayer(
                id,
                tenantId,
                ruc,
                legalName,
                tradeName,
                mainAddress,
                status,
                testEnabled,
                productionEnabled,
                createdAt,
                updatedAt
        );
    }

    public void updateFiscalIdentity(
            String legalName,
            String tradeName,
            String mainAddress,
            Instant now
    ) {
        this.legalName =
                validateRequiredText(
                        legalName,
                        "Legal name"
                );

        this.tradeName =
                normalizeOptionalText(tradeName);

        this.mainAddress =
                validateRequiredText(
                        mainAddress,
                        "Main address"
                );

        touch(now);
    }

    public void enableTestEnvironment(Instant now) {
        this.testEnabled = true;
        touch(now);
    }

    public void disableTestEnvironment(Instant now) {
        this.testEnabled = false;
        touch(now);
    }

    public void enableProductionEnvironment(Instant now) {
        this.productionEnabled = true;
        touch(now);
    }

    public void disableProductionEnvironment(Instant now) {
        this.productionEnabled = false;
        touch(now);
    }

    public void activate(Instant now) {
        this.status = TaxpayerStatus.ACTIVE;
        touch(now);
    }

    public void suspend(Instant now) {
        this.status = TaxpayerStatus.SUSPENDED;
        touch(now);
    }

    public void disable(Instant now) {
        this.status = TaxpayerStatus.DISABLED;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(
                now,
                "Update time cannot be null"
        );
    }

    private static String validateRequiredText(
            String value,
            String field
    ) {
        Objects.requireNonNull(
                value,
                field + " cannot be null"
        );

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return normalized;
    }

    private static String normalizeOptionalText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

}