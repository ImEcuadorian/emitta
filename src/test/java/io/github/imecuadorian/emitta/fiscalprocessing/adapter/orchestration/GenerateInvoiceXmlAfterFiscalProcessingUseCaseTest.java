package io.github.imecuadorian.emitta.fiscalprocessing.adapter.orchestration;

import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.model.ProcessFiscalDocumentResult;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.invoicexml.application.port.in.GenerateAndStoreInvoiceXmlUseCase;

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
class GenerateInvoiceXmlAfterFiscalProcessingUseCaseTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "77777777-7777-7777-7777-777777777777"
            );

    @Mock
    private ProcessFiscalDocumentUseCase delegate;

    @Mock
    private GenerateAndStoreInvoiceXmlUseCase
            generateAndStoreInvoiceXmlUseCase;

    private GenerateInvoiceXmlAfterFiscalProcessingUseCase
            useCase;

    @BeforeEach
    void setUp() {

        useCase =
                new GenerateInvoiceXmlAfterFiscalProcessingUseCase(
                        delegate,
                        generateAndStoreInvoiceXmlUseCase
                );
    }

    @Test
    void shouldGenerateAndStoreXmlAfterDocumentBecomesGenerating() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult result =
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
                result
        );

        ProcessFiscalDocumentResult returned =
                useCase.process(
                        command
                );

        assertSame(
                result,
                returned
        );

        InOrder order =
                inOrder(
                        delegate,
                        generateAndStoreInvoiceXmlUseCase
                );

        order.verify(
                delegate
        ).process(
                command
        );

        order.verify(
                generateAndStoreInvoiceXmlUseCase
        ).generateAndStore(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldRetryXmlGenerationForAlreadyGeneratingDocument() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        /*
         * processed=false represents a RabbitMQ redelivery after
         * the initial database transition already committed.
         */
        ProcessFiscalDocumentResult result =
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
                result
        );

        useCase.process(
                command
        );

        verify(
                generateAndStoreInvoiceXmlUseCase
        ).generateAndStore(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldNotGenerateXmlForDocumentOutsideGeneratingState() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult result =
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
                result
        );

        useCase.process(
                command
        );

        verify(
                generateAndStoreInvoiceXmlUseCase,
                never()
        ).generateAndStore(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldPropagateXmlStorageFailureForBrokerRetry() {

        ProcessFiscalDocumentCommand command =
                new ProcessFiscalDocumentCommand(
                        DOCUMENT_ID
                );

        ProcessFiscalDocumentResult result =
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
                result
        );

        RuntimeException failure =
                new RuntimeException(
                        "Object storage unavailable"
                );

        when(
                generateAndStoreInvoiceXmlUseCase
                        .generateAndStore(
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