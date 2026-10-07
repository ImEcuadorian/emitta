package io.github.imecuadorian.emitta.documentartifact.adapter.out.storage;

import io.github.imecuadorian.emitta.documentartifact.adapter.config.ArtifactStorageProperties;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoredArtifactObject;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class S3DocumentArtifactStorageAdapter
        implements DocumentArtifactStoragePort {

    private final S3Client s3Client;

    private final ArtifactStorageProperties properties;

    public S3DocumentArtifactStorageAdapter(
            S3Client s3Client,
            ArtifactStorageProperties properties
    ) {

        this.s3Client =
                Objects.requireNonNull(
                        s3Client
                );

        this.properties =
                Objects.requireNonNull(
                        properties
                );
    }

    @Override
    public StoredArtifactObject store(
            UUID documentId,
            DocumentArtifactType type,
            String contentType,
            String sha256,
            byte[] content
    ) {

        Objects.requireNonNull(
                documentId
        );

        Objects.requireNonNull(
                type
        );

        Objects.requireNonNull(
                content
        );

        String storageKey =
                buildStorageKey(
                        documentId,
                        type
                );

        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(
                                properties.bucket()
                        )
                        .key(
                                storageKey
                        )
                        .contentType(
                                contentType
                        )
                        .metadata(
                                java.util.Map.of(
                                        "sha256",
                                        sha256,
                                        "document-id",
                                        documentId.toString(),
                                        "artifact-type",
                                        type.name()
                                )
                        )
                        .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(
                        content
                )
        );

        return new StoredArtifactObject(
                storageKey
        );
    }

    private static String buildStorageKey(
            UUID documentId,
            DocumentArtifactType type
    ) {

        String fileName =
                switch (type) {

                    case UNSIGNED_XML ->
                            "unsigned.xml";

                    case SIGNED_XML ->
                            "signed.xml";

                    case AUTHORIZED_XML ->
                            "authorized.xml";

                    case RIDE_PDF ->
                            "ride.pdf";
                };

        return "documents/"
                + documentId
                + "/"
                + fileName
                .toLowerCase(
                        Locale.ROOT
                );
    }
}