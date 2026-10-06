package io.github.imecuadorian.emitta.fiscalprocessing.adapter.in.messaging;

import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FiscalDocumentListenerTest {

    @Test
    void shouldProcessDocumentReceivedMessage()
            throws Exception {

        ProcessFiscalDocumentUseCase useCase =
                mock(
                        ProcessFiscalDocumentUseCase.class
                );

        UUID documentId =
                UUID.randomUUID();

        FiscalDocumentListener listener =
                new FiscalDocumentListener(
                        useCase,
                        JsonMapper.builder()
                                .build()
                );

        String payload =
                """
                {
                  "documentId": "%s"
                }
                """.formatted(
                        documentId
                );

        Message message =
                new Message(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        new MessageProperties()
                );

        listener.onMessage(
                message
        );

        ArgumentCaptor<ProcessFiscalDocumentCommand> captor =
                ArgumentCaptor.forClass(
                        ProcessFiscalDocumentCommand.class
                );

        verify(
                useCase
        ).process(
                captor.capture()
        );

        assertEquals(
                documentId,
                captor.getValue()
                        .documentId()
        );
    }
}