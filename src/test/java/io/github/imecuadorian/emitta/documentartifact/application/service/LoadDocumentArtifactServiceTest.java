package io.github.imecuadorian.emitta.documentartifact.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactIntegrityException;
import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactNotFoundException;
import io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactRepositoryPort;
import io.github.imecuadorian.emitta.documentartifact.application.port.out.DocumentArtifactStoragePort;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadDocumentArtifactServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    @Mock
    private DocumentArtifactRepositoryPort repositoryPort;

    @Mock
    private DocumentArtifactStoragePort storagePort;

    private LoadDocumentArtifactService service;

    @BeforeEach
    void setUp() {

        service =
                new LoadDocumentArtifactService(
                        repositoryPort,
                        storagePort
                );
    }

    @Test
    void shouldLoadArtifactWhenContentMatchesPersistedMetadata()
            throws Exception {

        byte[] content =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact artifact =
                artifactFor(
                        content
                );

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.of(
                        artifact
                )
        );

        when(
                storagePort.load(
                        artifact.storageKey()
                )
        ).thenReturn(
                content
        );

        LoadedDocumentArtifact loaded =
                service.load(
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML
                );

        assertEquals(
                artifact,
                loaded.artifact()
        );

        assertArrayEquals(
                content,
                loaded.content()
        );
    }

    @Test
    void shouldRejectArtifactWhenSha256DoesNotMatch()
            throws Exception {

        byte[] expected =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] corrupted =
                "<facturx/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact artifact =
                artifactFor(
                        expected
                );

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.of(
                        artifact
                )
        );

        when(
                storagePort.load(
                        artifact.storageKey()
                )
        ).thenReturn(
                corrupted
        );

        assertThrows(
                DocumentArtifactIntegrityException.class,
                () ->
                        service.load(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        );
    }

    @Test
    void shouldRejectArtifactWhenSizeDoesNotMatch()
            throws Exception {

        byte[] expected =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] corrupted =
                "<factura>corrupted</factura>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact artifact =
                artifactFor(
                        expected
                );

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.of(
                        artifact
                )
        );

        when(
                storagePort.load(
                        artifact.storageKey()
                )
        ).thenReturn(
                corrupted
        );

        assertThrows(
                DocumentArtifactIntegrityException.class,
                () ->
                        service.load(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        );
    }

    @Test
    void shouldRejectWhenArtifactMetadataDoesNotExist() {

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                DocumentArtifactNotFoundException.class,
                () ->
                        service.load(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        );

        verify(
                storagePort,
                never()
        ).load(
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private static DocumentArtifact artifactFor(
            byte[] content
    ) throws Exception {

        String sha256 =
                HexFormat
                        .of()
                        .formatHex(
                                MessageDigest
                                        .getInstance(
                                                "SHA-256"
                                        )
                                        .digest(
                                                content
                                        )
                        );

        return new DocumentArtifact(
                UUID.randomUUID(),
                DOCUMENT_ID,
                DocumentArtifactType.UNSIGNED_XML,
                "application/xml",
                "documents/"
                        + DOCUMENT_ID
                        + "/unsigned.xml",
                sha256,
                content.length,
                Instant.parse(
                        "2026-10-07T03:00:00Z"
                )
        );
    }
}