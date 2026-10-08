package io.github.imecuadorian.emitta.documentartifact.application.model;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;

import java.util.Arrays;
import java.util.Objects;

public record LoadedDocumentArtifact(
        DocumentArtifact artifact,
        byte[] content
) {

    public LoadedDocumentArtifact {

        Objects.requireNonNull(
                artifact,
                "Artifact cannot be null"
        );

        Objects.requireNonNull(
                content,
                "Artifact content cannot be null"
        );

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