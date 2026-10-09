package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.document.domain.IdempotencyKey;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkDocumentSignedServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-07T03:00:00Z"
            );

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase artifactLoader;
    @Mock
    private io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort signatureVerifier;
    private MarkDocumentSignedService service;

    @BeforeEach
    void setUp() {

        service =
                new MarkDocumentSignedService(
                        documentRepository,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        ), artifactLoader, signatureVerifier
                );
    }

    @Test
    void shouldRejectInvalidSignedArtifactBeforeTransition() {
        Document document = document(DocumentStatus.GENERATING);
        when(documentRepository.findByIdForUpdate(DOCUMENT_ID)).thenReturn(Optional.of(document));
        var loaded = org.mockito.Mockito.mock(io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact.class);
        when(artifactLoader.load(DOCUMENT_ID, io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType.SIGNED_XML)).thenReturn(loaded);
        byte[] bytes = {1};
        when(loaded.content()).thenReturn(bytes);
        org.mockito.Mockito.doThrow(new io.github.imecuadorian.emitta.fiscalsigning.application.exception.XmlSignatureVerificationException("Missing comprobante reference"))
                .when(signatureVerifier).verify(bytes);
        assertThrows(io.github.imecuadorian.emitta.fiscalsigning.application.exception.XmlSignatureVerificationException.class,
                () -> service.markSigned(DOCUMENT_ID));
        assertEquals(DocumentStatus.GENERATING, document.getStatus());
        verify(documentRepository, never()).save(document);
    }

    @Test
    void shouldMarkGeneratingDocumentAsSigned() {

        Document document =
                document(
                        DocumentStatus.GENERATING
                );

        when(
                documentRepository.findByIdForUpdate(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        when(
                documentRepository.save(
                        document
                )
        ).thenReturn(
                document
        );

        var loaded = org.mockito.Mockito.mock(io.github.imecuadorian.emitta.documentartifact.application.model.LoadedDocumentArtifact.class);
        when(artifactLoader.load(DOCUMENT_ID, io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType.SIGNED_XML)).thenReturn(loaded);
        when(loaded.content()).thenReturn(new byte[]{1});
        Document result =
                service.markSigned(
                        DOCUMENT_ID
                );

        assertSame(
                document,
                result
        );

        assertEquals(
                DocumentStatus.SIGNED,
                result.getStatus()
        );

        assertEquals(
                NOW,
                result.getSignedAt()
        );

        verify(
                documentRepository
        ).save(
                document
        );
    }

    @Test
    void shouldReturnAlreadySignedDocumentWithoutSavingAgain() {

        Document document =
                document(
                        DocumentStatus.SIGNED
                );

        when(
                documentRepository.findByIdForUpdate(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        Document result =
                service.markSigned(
                        DOCUMENT_ID
                );

        assertSame(
                document,
                result
        );

        verify(
                documentRepository,
                never()
        ).save(
                document
        );
    }

    @Test
    void shouldRejectDocumentOutsideGeneratingState() {

        Document document =
                document(
                        DocumentStatus.QUEUED
                );

        when(
                documentRepository.findByIdForUpdate(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        document
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.markSigned(
                                DOCUMENT_ID
                        )
        );

        verify(
                documentRepository,
                never()
        ).save(
                document
        );
    }

    private static Document document(
            DocumentStatus status
    ) {

        Instant createdAt =
                Instant.parse(
                        "2026-10-06T20:00:00Z"
                );

        return Document.restore(
                DOCUMENT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.INVOICE,
                FiscalEnvironment.TEST,
                1L,
                "0710202601179001234500120010010000000011234567811",
                status,
                new IdempotencyKey(
                        "signing-test"
                ),
                createdAt,
                createdAt,
                createdAt,
                createdAt,
                status == DocumentStatus.SIGNED
                        ? createdAt
                        : null,
                null,
                null,
                null,
                createdAt,
                createdAt
        );
    }
}