package io.github.imecuadorian.emitta.establishment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EstablishmentTest {

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "d41de43c-eb87-445f-b02c-baeae6593375"
            );

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "5068da12-fc37-4e12-a50e-05adc629d7c7"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-05T22:00:00Z"
            );

    @Test
    void shouldCreateActiveEstablishment() {

        Establishment establishment =
                Establishment.create(
                        ESTABLISHMENT_ID,
                        TAXPAYER_ID,
                        new EstablishmentCode("001"),
                        "Matriz Quito",
                        "Av. Principal 123",
                        NOW
                );

        assertEquals(
                ESTABLISHMENT_ID,
                establishment.getId()
        );

        assertEquals(
                TAXPAYER_ID,
                establishment.getTaxpayerId()
        );

        assertEquals(
                "001",
                establishment.getCode().value()
        );

        assertEquals(
                EstablishmentStatus.ACTIVE,
                establishment.getStatus()
        );

        assertEquals(
                NOW,
                establishment.getCreatedAt()
        );
    }

    @Test
    void shouldNormalizeOptionalFields() {

        Establishment establishment =
                Establishment.create(
                        ESTABLISHMENT_ID,
                        TAXPAYER_ID,
                        new EstablishmentCode("001"),
                        "   ",
                        "   ",
                        NOW
                );

        assertNull(
                establishment.getName()
        );

        assertNull(
                establishment.getAddress()
        );
    }

    @Test
    void shouldDeactivateEstablishment() {

        Establishment establishment =
                Establishment.create(
                        ESTABLISHMENT_ID,
                        TAXPAYER_ID,
                        new EstablishmentCode("001"),
                        "Matriz Quito",
                        "Quito",
                        NOW
                );

        Instant deactivatedAt =
                Instant.parse(
                        "2026-10-05T23:00:00Z"
                );

        establishment.deactivate(
                deactivatedAt
        );

        assertEquals(
                EstablishmentStatus.INACTIVE,
                establishment.getStatus()
        );

        assertEquals(
                deactivatedAt,
                establishment.getUpdatedAt()
        );
    }
}