package io.github.imecuadorian.emitta.fiscalsigning.application.port.out;

import io.github.imecuadorian.emitta.fiscalsigning.application.model.ResolvedSigningKeyMaterial;

import java.time.Instant;
import java.util.UUID;

public interface SigningKeyMaterialPort {

    ResolvedSigningKeyMaterial loadForDocument(
            UUID documentId,
            Instant signingTime
    );
}