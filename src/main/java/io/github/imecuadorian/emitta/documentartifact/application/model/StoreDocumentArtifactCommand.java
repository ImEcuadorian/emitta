package io.github.imecuadorian.emitta.documentartifact.application.model;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record StoreDocumentArtifactCommand(
        UUID documentId,
        DocumentArtifactType type,
        String contentType,
        byte[] content
) {

    public StoreDocumentArtifactCommand {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                type,
                "Artifact type cannot be null"
        );

        if (contentType == null
                || contentType.isBlank()) {

            throw new IllegalArgumentException(
                    "Content type cannot be blank"
            );
        }

        if (content == null
                || content.length == 0) {

            throw new IllegalArgumentException(
                    "Artifact content cannot be empty"
            );
        }

        content =
                Arrays.copyOf(
                        content,
                        content.length
                );
    }

    @Override
    public byte[] content() {

        return Arrays.copyOf(
                content,
                content.length
        );
    }
}