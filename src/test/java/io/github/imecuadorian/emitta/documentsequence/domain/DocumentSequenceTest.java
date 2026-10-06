package io.github.imecuadorian.emitta.documentsequence.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentSequenceTest {

    private static final UUID SEQUENCE_ID =
            UUID.fromString(
                    "20a219e3-0c43-4228-9c11-01495dc92dde"
            );

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44ee5a49-334a-43c1-a57a-b1bb47ee60a4"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T01:00:00Z"
            );

    @Test
    void shouldCreateSequenceAtZero() {

        DocumentSequence sequence =
                DocumentSequence.create(
                        SEQUENCE_ID,
                        POINT_OF_ISSUE_ID,
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        NOW
                );

        assertEquals(
                SEQUENCE_ID,
                sequence.getId()
        );

        assertEquals(
                POINT_OF_ISSUE_ID,
                sequence.getPointOfIssueId()
        );

        assertEquals(
                DocumentType.INVOICE,
                sequence.getDocumentType()
        );

        assertEquals(
                FiscalEnvironment.TEST,
                sequence.getEnvironment()
        );

        assertEquals(
                0L,
                sequence.getCurrentValue()
        );

        assertEquals(
                NOW,
                sequence.getUpdatedAt()
        );
    }

    @Test
    void shouldRestoreExistingSequence() {

        DocumentSequence sequence =
                DocumentSequence.restore(
                        SEQUENCE_ID,
                        POINT_OF_ISSUE_ID,
                        DocumentType.INVOICE,
                        FiscalEnvironment.PRODUCTION,
                        782L,
                        NOW
                );

        assertEquals(
                782L,
                sequence.getCurrentValue()
        );

        assertEquals(
                FiscalEnvironment.PRODUCTION,
                sequence.getEnvironment()
        );
    }

    @Test
    void shouldRejectInvalidPersistedValue() {

        assertThrows(
                IllegalArgumentException.class,
                () -> DocumentSequence.restore(
                        SEQUENCE_ID,
                        POINT_OF_ISSUE_ID,
                        DocumentType.INVOICE,
                        FiscalEnvironment.TEST,
                        1_000_000_000L,
                        NOW
                )
        );
    }
}