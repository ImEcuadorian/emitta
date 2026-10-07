package io.github.imecuadorian.emitta.documentartifact.adapter.config;

import io.github.imecuadorian.emitta.documentartifact.adapter.out.storage.S3DocumentArtifactStorageAdapter;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(
        prefix = "emitta.artifacts.storage",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(
        ArtifactStorageProperties.class
)
public class ArtifactStorageConfiguration {

    @Bean
    S3Client artifactS3Client(
            ArtifactStorageProperties properties
    ) {

        return S3Client.builder()
                .endpointOverride(
                        URI.create(
                                properties.endpoint()
                        )
                )
                .region(
                        Region.of(
                                properties.region()
                        )
                )
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        properties.accessKey(),
                                        properties.secretKey()
                                )
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(
                                        properties.pathStyleAccess()
                                )
                                .build()
                )
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                )
                .build();
    }

    @Bean
    DocumentArtifactStoragePort documentArtifactStoragePort(
            S3Client artifactS3Client,
            ArtifactStorageProperties properties
    ) {

        return new S3DocumentArtifactStorageAdapter(
                artifactS3Client,
                properties
        );
    }
}