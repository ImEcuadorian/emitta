package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationPort;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryDocumentAuthorizationServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private SriAuthorizationPort sriAuthorizationPort;

    @Mock
    private Document document;

    private QueryDocumentAuthorizationService service;

    @BeforeEach
    void setUp() {

        service = new QueryDocumentAuthorizationService(
                documentRepository,
                sriAuthorizationPort
        );
    }

    @Test
    void shouldQuerySubmittedDocument() {

        stubDocumentForQuery(DocumentStatus.SUBMITTED);

        SriAuthorizationResult expected =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_FOUND,
                        null,
                        null,
                        null,
                        List.of()
                );

        when(
                sriAuthorizationPort.query(
                        new SriAuthorizationRequest(
                                FiscalEnvironment.TEST,
                                ACCESS_KEY
                        )
                )
        ).thenReturn(expected);

        SriAuthorizationResult actual =
                service.query(DOCUMENT_ID);

        assertSame(expected, actual);

        verify(sriAuthorizationPort).query(
                new SriAuthorizationRequest(
                        FiscalEnvironment.TEST,
                        ACCESS_KEY
                )
        );

        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldQueryRetryPendingDocument() {

        stubDocumentForQuery(DocumentStatus.RETRY_PENDING);

        SriAuthorizationResult expected =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_FOUND,
                        null,
                        null,
                        null,
                        List.of()
                );

        when(
                sriAuthorizationPort.query(
                        new SriAuthorizationRequest(
                                FiscalEnvironment.TEST,
                                ACCESS_KEY
                        )
                )
        ).thenReturn(expected);

        assertSame(
                expected,
                service.query(DOCUMENT_ID)
        );
    }

    @Test
    void shouldRejectSignedDocumentWithoutCallingSri() {

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        when(document.getStatus())
                .thenReturn(DocumentStatus.SIGNED);

        assertThrows(
                IllegalStateException.class,
                () -> service.query(DOCUMENT_ID)
        );

        verifyNoInteractions(sriAuthorizationPort);
    }

    @Test
    void shouldFailWhenDocumentDoesNotExist() {

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> service.query(DOCUMENT_ID)
        );

        verifyNoInteractions(sriAuthorizationPort);
    }

    private void stubDocumentForQuery(
            DocumentStatus status
    ) {

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        when(document.getStatus())
                .thenReturn(status);

        when(document.getEnvironment())
                .thenReturn(FiscalEnvironment.TEST);

        when(document.getAccessKey())
                .thenReturn(ACCESS_KEY);
    }
}