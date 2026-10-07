package io.github.imecuadorian.emitta.documentartifact.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentArtifactTest {

    private static final String SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void shouldCreateValidArtifact() {

        assertDoesNotThrow(
                () ->
                        new DocumentArtifact(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                DocumentArtifactType.UNSIGNED_XML,
                                "application/xml",
                                "documents/test/invoice.xml",
                                SHA256,
                                1024,
                                Instant.parse(
                                        "2026-10-06T23:00:00Z"
                                )
                        )
        );
    }

    @Test
    void shouldRejectInvalidSha256() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DocumentArtifact(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                DocumentArtifactType.UNSIGNED_XML,
                                "application/xml",
                                "documents/test/invoice.xml",
                                "invalid",
                                1024,
                                Instant.now()
                        )
        );
    }

    @Test
    void shouldRejectNegativeSize() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DocumentArtifact(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                DocumentArtifactType.RIDE_PDF,
                                "application/pdf",
                                "documents/test/ride.pdf",
                                SHA256,
                                -1,
                                Instant.now()
                        )
        );
    }

    @Test
    void shouldRejectBlankStorageKey() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DocumentArtifact(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                DocumentArtifactType.AUTHORIZED_XML,
                                "application/xml",
                                " ",
                                SHA256,
                                100,
                                Instant.now()
                        )
        );
    }
}