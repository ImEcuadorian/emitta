package io.github.imecuadorian.emitta.tenant.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "8c0d5de4-9fb0-4dd3-bdf2-04048ed44786"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-04T20:00:00Z");

    @Test
    void shouldCreateActiveTenant() {

        Tenant tenant = Tenant.create(
                TENANT_ID,
                "NEXORF",
                CREATED_AT
        );

        assertEquals(
                TENANT_ID,
                tenant.getId()
        );

        assertEquals(
                "NEXORF",
                tenant.getName()
        );

        assertEquals(
                TenantStatus.ACTIVE,
                tenant.getStatus()
        );

        assertEquals(
                CREATED_AT,
                tenant.getCreatedAt()
        );

        assertEquals(
                CREATED_AT,
                tenant.getUpdatedAt()
        );
    }

    @Test
    void shouldNormalizeTenantName() {

        Tenant tenant = Tenant.create(
                TENANT_ID,
                "   NEXORF   ",
                CREATED_AT
        );

        assertEquals(
                "NEXORF",
                tenant.getName()
        );
    }

    @Test
    void shouldRejectBlankTenantName() {

        assertThrows(
                IllegalArgumentException.class,
                () -> Tenant.create(
                        TENANT_ID,
                        "   ",
                        CREATED_AT
                )
        );
    }

    @Test
    void shouldSuspendTenant() {

        Tenant tenant = Tenant.create(
                TENANT_ID,
                "NEXORF",
                CREATED_AT
        );

        Instant suspendedAt =
                Instant.parse("2026-10-04T21:00:00Z");

        tenant.suspend(suspendedAt);

        assertEquals(
                TenantStatus.SUSPENDED,
                tenant.getStatus()
        );

        assertEquals(
                suspendedAt,
                tenant.getUpdatedAt()
        );
    }

    @Test
    void shouldRenameTenant() {

        Tenant tenant = Tenant.create(
                TENANT_ID,
                "NEXORF",
                CREATED_AT
        );

        Instant renamedAt =
                Instant.parse("2026-10-04T22:00:00Z");

        tenant.rename(
                "NEXORF Ecuador",
                renamedAt
        );

        assertEquals(
                "NEXORF Ecuador",
                tenant.getName()
        );

        assertEquals(
                renamedAt,
                tenant.getUpdatedAt()
        );
    }
}