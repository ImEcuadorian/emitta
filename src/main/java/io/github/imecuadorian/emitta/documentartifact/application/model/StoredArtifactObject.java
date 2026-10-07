package io.github.imecuadorian.emitta.documentartifact.application.model;

public record StoredArtifactObject(
        String storageKey
) {

    public StoredArtifactObject {

        if (storageKey == null
                || storageKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Storage key cannot be blank"
            );
        }
    }
}