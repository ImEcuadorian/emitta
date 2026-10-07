package io.github.imecuadorian.emitta.documentartifact.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DocumentArtifact(
        UUID id,
        UUID documentId,
        DocumentArtifactType type,
        String contentType,
        String storageKey,
        String sha256,
        long sizeBytes,
        Instant createdAt
) {

    public DocumentArtifact {

        Objects.requireNonNull(
                id,
                "Artifact id cannot be null"
        );

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                type,
                "Artifact type cannot be null"
        );

        requireText(
                contentType,
                "Content type cannot be blank"
        );

        requireText(
                storageKey,
                "Storage key cannot be blank"
        );

        requireSha256(
                sha256
        );

        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "Artifact size cannot be negative"
            );
        }

        Objects.requireNonNull(
                createdAt,
                "Created at cannot be null"
        );
    }

    private static void requireText(
            String value,
            String message
    ) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }
    }

    private static void requireSha256(
            String value
    ) {

        if (value == null
                || !value.matches(
                "^[0-9a-f]{64}$"
        )) {

            throw new IllegalArgumentException(
                    "SHA-256 must contain exactly 64 lowercase hexadecimal characters"
            );
        }
    }
}