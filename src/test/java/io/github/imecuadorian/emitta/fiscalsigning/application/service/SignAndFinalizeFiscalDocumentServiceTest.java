package io.github.imecuadorian.emitta.fiscalsigning.application.service;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.MarkDocumentSignedUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignFiscalDocumentUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignAndFinalizeFiscalDocumentServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    @Mock
    private SignFiscalDocumentUseCase
            signFiscalDocumentUseCase;

    @Mock
    private MarkDocumentSignedUseCase
            markDocumentSignedUseCase;

    @Mock
    private Document signedDocument;

    private SignAndFinalizeFiscalDocumentService service;

    @BeforeEach
    void setUp() {

        service =
                new SignAndFinalizeFiscalDocumentService(
                        signFiscalDocumentUseCase,
                        markDocumentSignedUseCase
                );
    }

    @Test
    void shouldPersistSignedArtifactBeforeMarkingDocumentAsSigned() {

        when(
                markDocumentSignedUseCase.markSigned(
                        DOCUMENT_ID
                )
        ).thenReturn(
                signedDocument
        );

        Document result =
                service.signAndFinalize(
                        DOCUMENT_ID
                );

        assertSame(
                signedDocument,
                result
        );

        InOrder order =
                inOrder(
                        signFiscalDocumentUseCase,
                        markDocumentSignedUseCase
                );

        order.verify(
                signFiscalDocumentUseCase
        ).sign(
                DOCUMENT_ID
        );

        order.verify(
                markDocumentSignedUseCase
        ).markSigned(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldNotMarkDocumentSignedWhenSigningFails() {

        RuntimeException failure =
                new RuntimeException(
                        "Signing failed"
                );

        when(
                signFiscalDocumentUseCase.sign(
                        DOCUMENT_ID
                )
        ).thenThrow(
                failure
        );

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.signAndFinalize(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                markDocumentSignedUseCase,
                never()
        ).markSigned(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldPropagateFinalizationFailureForRedeliveryRecovery() {

        RuntimeException failure =
                new RuntimeException(
                        "Database unavailable"
                );

        when(
                markDocumentSignedUseCase.markSigned(
                        DOCUMENT_ID
                )
        ).thenThrow(
                failure
        );

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.signAndFinalize(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                signFiscalDocumentUseCase
        ).sign(
                DOCUMENT_ID
        );
    }
}