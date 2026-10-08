package io.github.imecuadorian.emitta.documentartifact.adapter.out.storage;

import io.github.imecuadorian.emitta.documentartifact.adapter.config.ArtifactStorageProperties;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoredArtifactObject;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("object-storage-integration")
class S3DocumentArtifactStorageAdapterIntegrationTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "77777777-7777-7777-7777-777777777777"
            );

    private static S3Client s3Client;

    private static ArtifactStorageProperties properties;

    @BeforeAll
    static void setUp() {

        String endpoint =
                requiredEnvironmentVariable(
                        "EMITTA_ARTIFACT_STORAGE_ENDPOINT"
                );

        String region =
                requiredEnvironmentVariable(
                        "EMITTA_ARTIFACT_STORAGE_REGION"
                );

        String bucket =
                requiredEnvironmentVariable(
                        "EMITTA_ARTIFACT_STORAGE_BUCKET"
                );

        String accessKey =
                requiredEnvironmentVariable(
                        "EMITTA_ARTIFACT_STORAGE_ACCESS_KEY"
                );

        String secretKey =
                requiredEnvironmentVariable(
                        "EMITTA_ARTIFACT_STORAGE_SECRET_KEY"
                );

        boolean pathStyleAccess =
                Boolean.parseBoolean(
                        requiredEnvironmentVariable(
                                "EMITTA_ARTIFACT_STORAGE_PATH_STYLE"
                        )
                );

        properties =
                new ArtifactStorageProperties(
                        endpoint,
                        region,
                        bucket,
                        accessKey,
                        secretKey,
                        pathStyleAccess
                );

        s3Client =
                S3Client.builder()
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

        ensureBucketExists();
    }

    @AfterAll
    static void tearDown() {

        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    void shouldStoreAndRetrieveUnsignedXmlFromMinio() throws Exception {

        byte[] content =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <factura id="comprobante" version="2.1.0"/>
                """
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        String sha256 =
                HexFormat.of()
                        .formatHex(
                                MessageDigest
                                        .getInstance(
                                                "SHA-256"
                                        )
                                        .digest(
                                                content
                                        )
                        );

        S3DocumentArtifactStorageAdapter adapter =
                new S3DocumentArtifactStorageAdapter(
                        s3Client,
                        properties
                );

        StoredArtifactObject stored =
                adapter.store(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML,
                        "application/xml",
                        sha256,
                        content
                );

        String expectedKey =
                "documents/"
                        + DOCUMENT_ID
                        + "/unsigned.xml";

        assertEquals(
                expectedKey,
                stored.storageKey()
        );

        try {

            ResponseBytes<GetObjectResponse> downloaded =
                    s3Client.getObjectAsBytes(
                            GetObjectRequest.builder()
                                    .bucket(
                                            properties.bucket()
                                    )
                                    .key(
                                            stored.storageKey()
                                    )
                                    .build()
                    );

            assertArrayEquals(
                    content,
                    downloaded.asByteArray()
            );

            assertEquals(
                    "application/xml",
                    downloaded.response()
                            .contentType()
            );

            assertEquals(
                    sha256,
                    downloaded.response()
                            .metadata()
                            .get(
                                    "sha256"
                            )
            );

            assertEquals(
                    DOCUMENT_ID.toString(),
                    downloaded.response()
                            .metadata()
                            .get(
                                    "document-id"
                            )
            );

            assertEquals(
                    DocumentArtifactType.UNSIGNED_XML.name(),
                    downloaded.response()
                            .metadata()
                            .get(
                                    "artifact-type"
                            )
            );

        } finally {

            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(
                                    properties.bucket()
                            )
                            .key(
                                    stored.storageKey()
                            )
                            .build()
            );
        }
    }

    private static void ensureBucketExists() {

        try {

            s3Client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(
                                    properties.bucket()
                            )
                            .build()
            );

        } catch (S3Exception exception) {

            if (exception.statusCode() != 404) {
                throw exception;
            }

            s3Client.createBucket(
                    CreateBucketRequest.builder()
                            .bucket(
                                    properties.bucket()
                            )
                            .build()
            );
        }
    }

    private static String requiredEnvironmentVariable(
            String name
    ) {

        String value =
                System.getenv(
                        name
                );

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    "Required environment variable is missing: "
                            + name
            );
        }

        return value;
    }

}