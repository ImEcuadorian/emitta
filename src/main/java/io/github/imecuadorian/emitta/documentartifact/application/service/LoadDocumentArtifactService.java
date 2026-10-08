package io.github.imecuadorian.emitta.documentartifact.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactIntegrityException;
import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactNotFoundException;
import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactRepositoryPort;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class LoadDocumentArtifactService
        implements LoadDocumentArtifactUseCase {

    private final DocumentArtifactRepositoryPort
            repositoryPort;

    private final DocumentArtifactStoragePort
            storagePort;

    public LoadDocumentArtifactService(
            DocumentArtifactRepositoryPort repositoryPort,
            DocumentArtifactStoragePort storagePort
    ) {

        this.repositoryPort =
                Objects.requireNonNull(
                        repositoryPort
                );

        this.storagePort =
                Objects.requireNonNull(
                        storagePort
                );
    }

    @Override
    public LoadedDocumentArtifact load(
            UUID documentId,
            DocumentArtifactType type
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                type,
                "Artifact type cannot be null"
        );

        DocumentArtifact artifact =
                repositoryPort
                        .findByDocumentIdAndType(
                                documentId,
                                type
                        )
                        .orElseThrow(
                                () ->
                                        new DocumentArtifactNotFoundException(
                                                documentId,
                                                type
                                        )
                        );

        byte[] content =
                storagePort.load(
                        artifact.storageKey()
                );

        verifyIntegrity(
                artifact,
                content
        );

        return new LoadedDocumentArtifact(
                artifact,
                content
        );
    }

    private static void verifyIntegrity(
            DocumentArtifact artifact,
            byte[] content
    ) {

        if (content == null) {

            throw new DocumentArtifactIntegrityException(
                    artifact.documentId(),
                    artifact.type(),
                    "Artifact storage returned null content"
            );
        }

        if (
                content.length
                        != artifact.sizeBytes()
        ) {

            throw new DocumentArtifactIntegrityException(
                    artifact.documentId(),
                    artifact.type(),
                    "Artifact size does not match persisted metadata"
            );
        }

        String actualSha256 =
                sha256(
                        content
                );

        if (
                !artifact.sha256()
                        .equals(
                                actualSha256
                        )
        ) {

            throw new DocumentArtifactIntegrityException(
                    artifact.documentId(),
                    artifact.type(),
                    "Artifact SHA-256 does not match persisted metadata"
            );
        }
    }

    private static String sha256(
            byte[] content
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            digest.digest(
                                    content
                            )
                    );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}