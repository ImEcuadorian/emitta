package io.github.imecuadorian.emitta.pointofissue.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PointOfIssueTest {

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44ee5a49-334a-43c1-a57a-b1bb47ee60a4"
            );

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "d41de43c-eb87-445f-b02c-baeae6593375"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-05T23:45:00Z"
            );

    @Test
    void shouldCreateActivePointOfIssue() {

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        POINT_OF_ISSUE_ID,
                        ESTABLISHMENT_ID,
                        new PointOfIssueCode("001"),
                        "Caja Principal",
                        NOW
                );

        assertEquals(
                POINT_OF_ISSUE_ID,
                pointOfIssue.getId()
        );

        assertEquals(
                ESTABLISHMENT_ID,
                pointOfIssue.getEstablishmentId()
        );

        assertEquals(
                "001",
                pointOfIssue.getCode().value()
        );

        assertEquals(
                "Caja Principal",
                pointOfIssue.getName()
        );

        assertEquals(
                PointOfIssueStatus.ACTIVE,
                pointOfIssue.getStatus()
        );

        assertEquals(
                NOW,
                pointOfIssue.getCreatedAt()
        );
    }

    @Test
    void shouldNormalizeOptionalName() {

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        POINT_OF_ISSUE_ID,
                        ESTABLISHMENT_ID,
                        new PointOfIssueCode("001"),
                        "   ",
                        NOW
                );

        assertNull(
                pointOfIssue.getName()
        );
    }

    @Test
    void shouldDeactivatePointOfIssue() {

        PointOfIssue pointOfIssue =
                PointOfIssue.create(
                        POINT_OF_ISSUE_ID,
                        ESTABLISHMENT_ID,
                        new PointOfIssueCode("001"),
                        "Caja Principal",
                        NOW
                );

        Instant deactivatedAt =
                Instant.parse(
                        "2026-10-06T00:00:00Z"
                );

        pointOfIssue.deactivate(
                deactivatedAt
        );

        assertEquals(
                PointOfIssueStatus.INACTIVE,
                pointOfIssue.getStatus()
        );

        assertEquals(
                deactivatedAt,
                pointOfIssue.getUpdatedAt()
        );
    }
}