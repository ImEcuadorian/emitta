package io.github.imecuadorian.emitta.fiscalprocessing.adapter.orchestration;

import io.github.imecuadorian.emitta.document.domain.Document;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.in.SignAndFinalizeFiscalDocumentUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignAfterInvoiceXmlGenerationUseCaseTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "77777777-7777-7777-7777-777777777777"
            );

    @Mock
    private ProcessFiscalDocumentUseCase delegate;

    @Mock
    private SignAndFinalizeFiscalDocumentUseCase
            signAndFinalizeFiscalDocumentUseCase;

    @Mock
    private Document signedDocument;

    private SignAfterInvoiceXmlGenerationUseCase useCase;

    @BeforeEach
    void setUp() {

        useCase =
                new SignAfterInvoiceXmlGenerationUseCase(
                        delegate,
                        signAndFinalizeFiscalDocumentUseCase
                );
    }

    @Test
    void shouldSignAndFinalizeGeneratingDocument() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult generating =
                new ProcessFiscalDocumentResult(
                        DOCUMENT_ID,
                        DocumentStatus.GENERATING,
                        true
                );

        when(
                delegate.process(
                        command
                )
        ).thenReturn(
                generating
        );

        when(
                signAndFinalizeFiscalDocumentUseCase
                        .signAndFinalize(
                                DOCUMENT_ID
                        )
        ).thenReturn(
                signedDocument
        );

        when(
                signedDocument.getStatus()
        ).thenReturn(
                DocumentStatus.SIGNED
        );

        ProcessFiscalDocumentResult result =
                useCase.process(
                        command
                );

        assertEquals(
                DocumentStatus.SIGNED,
                result.status()
        );

        assertEquals(
                true,
                result.processed()
        );

        InOrder order =
                inOrder(
                        delegate,
                        signAndFinalizeFiscalDocumentUseCase
                );

        order.verify(
                delegate
        ).process(
                command
        );

        order.verify(
                signAndFinalizeFiscalDocumentUseCase
        ).signAndFinalize(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldRecoverGeneratingDocumentOnRedelivery() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult generating =
                new ProcessFiscalDocumentResult(
                        DOCUMENT_ID,
                        DocumentStatus.GENERATING,
                        false
                );

        when(
                delegate.process(
                        command
                )
        ).thenReturn(
                generating
        );

        when(
                signAndFinalizeFiscalDocumentUseCase
                        .signAndFinalize(
                                DOCUMENT_ID
                        )
        ).thenReturn(
                signedDocument
        );

        when(
                signedDocument.getStatus()
        ).thenReturn(
                DocumentStatus.SIGNED
        );

        ProcessFiscalDocumentResult result =
                useCase.process(
                        command
                );

        assertEquals(
                DocumentStatus.SIGNED,
                result.status()
        );

        assertEquals(
                false,
                result.processed()
        );

        verify(
                signAndFinalizeFiscalDocumentUseCase
        ).signAndFinalize(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldNotSignDocumentOutsideGeneratingState() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult signed =
                new ProcessFiscalDocumentResult(
                        DOCUMENT_ID,
                        DocumentStatus.SIGNED,
                        false
                );

        when(
                delegate.process(
                        command
                )
        ).thenReturn(
                signed
        );

        ProcessFiscalDocumentResult result =
                useCase.process(
                        command
                );

        assertSame(
                signed,
                result
        );

        verify(
                signAndFinalizeFiscalDocumentUseCase,
                never()
        ).signAndFinalize(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldPropagateSigningFailureForBrokerRedelivery() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult generating =
                new ProcessFiscalDocumentResult(
                        DOCUMENT_ID,
                        DocumentStatus.GENERATING,
                        true
                );

        when(
                delegate.process(
                        command
                )
        ).thenReturn(
                generating
        );

        RuntimeException failure =
                new RuntimeException(
                        "Signing infrastructure unavailable"
                );

        when(
                signAndFinalizeFiscalDocumentUseCase
                        .signAndFinalize(
                                DOCUMENT_ID
                        )
        ).thenThrow(
                failure
        );

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                useCase.process(
                                        command
                                )
                );

        assertSame(
                failure,
                thrown
        );
    }
}