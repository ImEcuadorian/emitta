package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

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
class MarkDocumentSubmittedServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-07T22:00:00Z"
            );

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private Document document;

    private MarkDocumentSubmittedService service;

    @BeforeEach
    void setUp() {

        service =
                new MarkDocumentSubmittedService(
                        documentRepository,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void shouldMarkSignedDocumentAsSubmitted() {

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
                document.getStatus()
        ).thenReturn(
                DocumentStatus.SIGNED
        );

        when(
                documentRepository.save(
                        document
                )
        ).thenReturn(
                document
        );

        Document result =
                service.markSubmitted(
                        DOCUMENT_ID
                );

        assertSame(
                document,
                result
        );

        verify(
                document
        ).markSubmitted(
                NOW
        );

        verify(
                documentRepository
        ).save(
                document
        );
    }

    @Test
    void shouldReturnAlreadySubmittedDocumentWithoutSavingAgain() {

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
                document.getStatus()
        ).thenReturn(
                DocumentStatus.SUBMITTED
        );

        Document result =
                service.markSubmitted(
                        DOCUMENT_ID
                );

        assertSame(
                document,
                result
        );

        verify(
                document,
                never()
        ).markSubmitted(
                org.mockito.ArgumentMatchers.any()
        );

        verify(
                documentRepository,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldRejectDocumentOutsideSignedOrSubmittedState() {

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
                document.getStatus()
        ).thenReturn(
                DocumentStatus.GENERATING
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.markSubmitted(
                                DOCUMENT_ID
                        )
        );

        verify(
                documentRepository,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldFailWhenDocumentDoesNotExist() {

        when(
                documentRepository.findByIdForUpdate(
                        DOCUMENT_ID
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.markSubmitted(
                                DOCUMENT_ID
                        )
        );
    }

    @Test
    void shouldResubmitRetryPendingDocument() {

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
                document.getStatus()
        ).thenReturn(
                DocumentStatus.RETRY_PENDING
        );

        when(
                documentRepository.save(
                        document
                )
        ).thenReturn(
                document
        );

        Document result =
                service.markSubmitted(
                        DOCUMENT_ID
                );

        assertSame(
                document,
                result
        );

        verify(
                document
        ).markSubmitted(
                NOW
        );

        verify(
                documentRepository
        ).save(
                document
        );
    }
}