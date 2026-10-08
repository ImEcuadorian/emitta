package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationEvidence;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationEvidencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarkDocumentAuthorizedServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-08T01:00:00Z"
            );

    private static final Instant SRI_AUTHORIZED_AT =
            Instant.parse("2026-10-07T22:15:00Z");

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private Document document;

    @Mock
    private SriAuthorizationEvidencePort sriAuthorizationEvidencePort;

    private MarkDocumentAuthorizedService service;

    @BeforeEach
    void setUp() {

        service =
                new MarkDocumentAuthorizedService(
                        documentRepository,
                        sriAuthorizationEvidencePort,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void shouldAuthorizeSubmittedDocument() {

        stubDocument(DocumentStatus.SUBMITTED);
        stubValidEvidence();

        when(documentRepository.save(document))
                .thenReturn(document);

        Document result =
                service.markAuthorized(DOCUMENT_ID);

        assertSame(document, result);

        verify(document).markAuthorized(
                SRI_AUTHORIZED_AT,
                NOW
        );

        verify(documentRepository).save(document);
    }

    @Test
    void shouldAuthorizeRetryPendingDocument() {

        stubDocument(DocumentStatus.RETRY_PENDING);
        stubValidEvidence();

        when(documentRepository.save(document))
                .thenReturn(document);

        Document result =
                service.markAuthorized(DOCUMENT_ID);

        assertSame(document, result);

        verify(document).markAuthorized(
                SRI_AUTHORIZED_AT,
                NOW
        );

        verify(documentRepository).save(document);
    }

    @Test
    void shouldReturnAlreadyAuthorizedDocumentWithoutSaving() {

        stubDocument(DocumentStatus.AUTHORIZED);

        Document result =
                service.markAuthorized(DOCUMENT_ID);

        assertSame(document, result);

        verify(document, never())
                .markAuthorized(any());

        verify(documentRepository, never())
                .save(any());
    }

    @Test
    void shouldRejectSignedDocument() {

        stubDocument(DocumentStatus.SIGNED);

        assertThrows(
                IllegalStateException.class,
                () -> service.markAuthorized(DOCUMENT_ID)
        );

        verify(documentRepository, never())
                .save(any());
    }

    @Test
    void shouldFailWhenDocumentDoesNotExist() {

        when(documentRepository.findByIdForUpdate(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> service.markAuthorized(DOCUMENT_ID)
        );

        verify(documentRepository, never())
                .save(any());
    }

    private void stubDocument(
            DocumentStatus status
    ) {

        when(documentRepository.findByIdForUpdate(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        when(document.getStatus())
                .thenReturn(status);
    }

    @Test
    void shouldRejectAuthorizationWithoutSriEvidence() {

        stubDocument(DocumentStatus.SUBMITTED);

        when(sriAuthorizationEvidencePort.findByDocumentId(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> service.markAuthorized(DOCUMENT_ID)
        );

        verify(documentRepository, never())
                .save(any());

        verify(document, never())
                .markAuthorized(
                        any(Instant.class),
                        any(Instant.class)
                );
    }

    private void stubValidEvidence() {

        SriAuthorizationEvidence evidence =
                new SriAuthorizationEvidence(
                        DOCUMENT_ID,
                        ACCESS_KEY,
                        SRI_AUTHORIZED_AT,
                        FiscalEnvironment.TEST,
                        "a".repeat(64),
                        List.of()
                );

        when(sriAuthorizationEvidencePort.findByDocumentId(DOCUMENT_ID))
                .thenReturn(Optional.of(evidence));

        when(document.getAccessKey())
                .thenReturn(ACCESS_KEY);

        when(document.getEnvironment())
                .thenReturn(FiscalEnvironment.TEST);
    }
}