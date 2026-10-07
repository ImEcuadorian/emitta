package io.github.imecuadorian.emitta.documentartifact.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactIntegrityException;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoredArtifactObject;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactRepositoryPort;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class StoreDocumentArtifactService
        implements StoreDocumentArtifactUseCase {

    private final DocumentArtifactRepositoryPort
            repositoryPort;

    private final DocumentArtifactStoragePort
            storagePort;

    private final Clock clock;

    public StoreDocumentArtifactService(
            DocumentArtifactRepositoryPort repositoryPort,
            DocumentArtifactStoragePort storagePort,
            Clock clock
    ) {

        this.repositoryPort =
                Objects.requireNonNull(
                        repositoryPort
                );

        this.storagePort =
                Objects.requireNonNull(
                        storagePort
                );

        this.clock =
                Objects.requireNonNull(
                        clock
                );
    }

    @Override
    public DocumentArtifact store(
            StoreDocumentArtifactCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Command cannot be null"
        );

        byte[] content =
                command.content();

        String sha256 =
                sha256(
                        content
                );

        var existing =
                repositoryPort
                        .findByDocumentIdAndType(
                                command.documentId(),
                                command.type()
                        );

        if (existing.isPresent()) {

            DocumentArtifact artifact =
                    existing.orElseThrow();

            if (!artifact.sha256()
                    .equals(
                            sha256
                    )) {

                throw new DocumentArtifactIntegrityException(
                        command.documentId(),
                        command.type()
                );
            }

            return artifact;
        }

        StoredArtifactObject stored =
                storagePort.store(
                        command.documentId(),
                        command.type(),
                        command.contentType(),
                        sha256,
                        content
                );

        DocumentArtifact artifact =
                new DocumentArtifact(
                        UUID.randomUUID(),
                        command.documentId(),
                        command.type(),
                        command.contentType(),
                        stored.storageKey(),
                        sha256,
                        content.length,
                        clock.instant()
                );

        return repositoryPort.save(
                artifact
        );
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