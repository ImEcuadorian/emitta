package io.github.imecuadorian.emitta.sriauthorization.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SriAuthorizationEvidenceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    private static final Instant AUTHORIZED_AT =
            Instant.parse("2026-10-08T01:00:00Z");

    private static SriAuthorizationEvidence create(
            List<SriAuthorizationMessage> messages
    ) {

        return new SriAuthorizationEvidence(
                DOCUMENT_ID,
                ACCESS_KEY,
                AUTHORIZED_AT,
                FiscalEnvironment.TEST,
                "a".repeat(64),
                messages
        );
    }

    @Test
    void shouldCreateValidEvidence() {

        SriAuthorizationEvidence evidence =
                create(List.of());

        assertEquals(DOCUMENT_ID, evidence.documentId());
        assertEquals(ACCESS_KEY, evidence.authorizationNumber());
        assertEquals(AUTHORIZED_AT, evidence.authorizedAt());
        assertEquals(FiscalEnvironment.TEST, evidence.environment());
    }

    @Test
    void shouldRejectInvalidSha256() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new SriAuthorizationEvidence(
                        DOCUMENT_ID,
                        ACCESS_KEY,
                        AUTHORIZED_AT,
                        FiscalEnvironment.TEST,
                        "invalid",
                        List.of()
                )
        );
    }

    @Test
    void shouldProtectMessagesFromModification() {

        List<SriAuthorizationMessage> messages =
                new ArrayList<>();

        messages.add(
                new SriAuthorizationMessage(
                        "60",
                        "Test environment",
                        null,
                        "ADVERTENCIA"
                )
        );

        SriAuthorizationEvidence evidence =
                create(messages);

        messages.clear();

        assertEquals(1, evidence.messages().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> evidence.messages().clear()
        );
    }
}