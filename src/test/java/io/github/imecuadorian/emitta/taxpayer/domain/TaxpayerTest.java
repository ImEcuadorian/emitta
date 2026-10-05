package io.github.imecuadorian.emitta.taxpayer.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaxpayerTest {

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "5068da12-fc37-4e12-a50e-05adc629d7c7"
            );

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "4d34da33-c6e4-4379-8ab0-19fef817cc67"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-05T12:00:00Z"
            );

    @Test
    void shouldCreateTaxpayerInTestEnvironment() {

        Taxpayer taxpayer =
                Taxpayer.create(
                        TAXPAYER_ID,
                        TENANT_ID,
                        new Ruc("1790012345001"),
                        "NEXORF S.A.S.",
                        "NEXORF",
                        "Quito",
                        NOW
                );

        assertEquals(
                TAXPAYER_ID,
                taxpayer.getId()
        );

        assertEquals(
                TENANT_ID,
                taxpayer.getTenantId()
        );

        assertEquals(
                "1790012345001",
                taxpayer.getRuc().value()
        );

        assertEquals(
                TaxpayerStatus.ACTIVE,
                taxpayer.getStatus()
        );

        assertTrue(
                taxpayer.isTestEnabled()
        );

        assertFalse(
                taxpayer.isProductionEnabled()
        );
    }

    @Test
    void shouldEnableProductionEnvironment() {

        Taxpayer taxpayer =
                Taxpayer.create(
                        TAXPAYER_ID,
                        TENANT_ID,
                        new Ruc("1790012345001"),
                        "NEXORF S.A.S.",
                        "NEXORF",
                        "Quito",
                        NOW
                );

        Instant enabledAt =
                Instant.parse(
                        "2026-10-05T13:00:00Z"
                );

        taxpayer.enableProductionEnvironment(
                enabledAt
        );

        assertTrue(
                taxpayer.isProductionEnabled()
        );

        assertEquals(
                enabledAt,
                taxpayer.getUpdatedAt()
        );
    }

    @Test
    void shouldNormalizeOptionalTradeName() {

        Taxpayer taxpayer =
                Taxpayer.create(
                        TAXPAYER_ID,
                        TENANT_ID,
                        new Ruc("1790012345001"),
                        "NEXORF S.A.S.",
                        "   ",
                        "Quito",
                        NOW
                );

        assertNull(taxpayer.getTradeName());
    }
}