package io.github.imecuadorian.emitta.documentartifact.application.service;

import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactIntegrityException;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoredArtifactObject;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreDocumentArtifactServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T23:30:00Z"
            );

    @Mock
    private DocumentArtifactRepositoryPort repositoryPort;

    @Mock
    private DocumentArtifactStoragePort storagePort;

    private StoreDocumentArtifactService service;

    @BeforeEach
    void setUp() {

        service =
                new StoreDocumentArtifactService(
                        repositoryPort,
                        storagePort,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void shouldStoreNewArtifact() {

        byte[] content =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                storagePort.store(
                        eq(
                                DOCUMENT_ID
                        ),
                        eq(
                                DocumentArtifactType.UNSIGNED_XML
                        ),
                        eq(
                                "application/xml"
                        ),
                        anyString(),
                        any()
                )
        ).thenReturn(
                new StoredArtifactObject(
                        "documents/test/unsigned.xml"
                )
        );

        when(
                repositoryPort.save(
                        any()
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(
                                0
                        )
        );

        DocumentArtifact result =
                service.store(
                        new StoreDocumentArtifactCommand(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML,
                                "application/xml",
                                content
                        )
                );

        assertEquals(
                DOCUMENT_ID,
                result.documentId()
        );

        assertEquals(
                DocumentArtifactType.UNSIGNED_XML,
                result.type()
        );

        assertEquals(
                "application/xml",
                result.contentType()
        );

        assertEquals(
                "documents/test/unsigned.xml",
                result.storageKey()
        );

        assertEquals(
                64,
                result.sha256()
                        .length()
        );

        assertEquals(
                content.length,
                result.sizeBytes()
        );

        assertEquals(
                NOW,
                result.createdAt()
        );

        verify(
                repositoryPort
        ).save(
                any()
        );
    }

    @Test
    void shouldReturnExistingArtifactWhenContentIsIdentical() {

        byte[] content =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        DocumentArtifact existing =
                new DocumentArtifact(
                        UUID.randomUUID(),
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML,
                        "application/xml",
                        "documents/existing/unsigned.xml",
                        sha256OfFactura(),
                        content.length,
                        NOW
                );

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.of(
                        existing
                )
        );

        DocumentArtifact result =
                service.store(
                        new StoreDocumentArtifactCommand(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML,
                                "application/xml",
                                content
                        )
                );

        assertSame(
                existing,
                result
        );

        verify(
                storagePort,
                never()
        ).store(
                any(),
                any(),
                anyString(),
                anyString(),
                any()
        );

        verify(
                repositoryPort,
                never()
        ).save(
                any()
        );
    }

    @Test
    void shouldRejectExistingArtifactWithDifferentContent() {

        DocumentArtifact existing =
                new DocumentArtifact(
                        UUID.randomUUID(),
                        DOCUMENT_ID,
                        DocumentArtifactType.UNSIGNED_XML,
                        "application/xml",
                        "documents/existing/unsigned.xml",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        100,
                        NOW
                );

        when(
                repositoryPort
                        .findByDocumentIdAndType(
                                DOCUMENT_ID,
                                DocumentArtifactType.UNSIGNED_XML
                        )
        ).thenReturn(
                Optional.of(
                        existing
                )
        );

        assertThrows(
                DocumentArtifactIntegrityException.class,
                () ->
                        service.store(
                                new StoreDocumentArtifactCommand(
                                        DOCUMENT_ID,
                                        DocumentArtifactType.UNSIGNED_XML,
                                        "application/xml",
                                        "<different/>"
                                                .getBytes(
                                                        StandardCharsets.UTF_8
                                                )
                                )
                        )
        );

        verify(
                storagePort,
                never()
        ).store(
                any(),
                any(),
                anyString(),
                anyString(),
                any()
        );

        verify(
                repositoryPort,
                never()
        ).save(
                any()
        );
    }

    private static String sha256OfFactura() {

        return "0a649d0d3ae4f28c4e0e2f45498e2c8d"
                + "81c682dafc2f9ba6b890f494cba30b80";
    }
}