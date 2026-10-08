package io.github.imecuadorian.emitta.fiscalsigning.application.model;

import java.util.Objects;
import java.util.UUID;

public record ResolvedSigningKeyMaterial(
        UUID certificateId,
        SigningKeyMaterial keyMaterial
) {

    public ResolvedSigningKeyMaterial {

        Objects.requireNonNull(
                certificateId,
                "Certificate id cannot be null"
        );

        Objects.requireNonNull(
                keyMaterial,
                "Signing key material cannot be null"
        );
    }
}