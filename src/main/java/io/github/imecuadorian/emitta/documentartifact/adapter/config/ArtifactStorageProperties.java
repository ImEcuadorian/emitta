package io.github.imecuadorian.emitta.documentartifact.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "emitta.artifacts.storage"
)
public record ArtifactStorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess
) {

    public ArtifactStorageProperties {

        if (endpoint == null
                || endpoint.isBlank()) {

            throw new IllegalArgumentException(
                    "Artifact storage endpoint cannot be blank"
            );
        }

        if (region == null
                || region.isBlank()) {

            throw new IllegalArgumentException(
                    "Artifact storage region cannot be blank"
            );
        }

        if (bucket == null
                || bucket.isBlank()) {

            throw new IllegalArgumentException(
                    "Artifact storage bucket cannot be blank"
            );
        }

        if (accessKey == null
                || accessKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Artifact storage access key cannot be blank"
            );
        }

        if (secretKey == null
                || secretKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Artifact storage secret key cannot be blank"
            );
        }
    }
}