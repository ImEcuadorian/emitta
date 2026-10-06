package io.github.imecuadorian.emitta.fiscalprocessing.adapter.in.messaging;

import io.github.imecuadorian.emitta.fiscalprocessing.application.command.ProcessFiscalDocumentCommand;
import io.github.imecuadorian.emitta.fiscalprocessing.application.port.in.ProcessFiscalDocumentUseCase;
import io.github.imecuadorian.emitta.outbox.adapter.config.RabbitOutboxConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "emitta.fiscal.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FiscalDocumentListener {

    private final ProcessFiscalDocumentUseCase
            processFiscalDocumentUseCase;

    private final JsonMapper jsonMapper;

    public FiscalDocumentListener(
            ProcessFiscalDocumentUseCase processFiscalDocumentUseCase,
            JsonMapper jsonMapper
    ) {
        this.processFiscalDocumentUseCase =
                Objects.requireNonNull(
                        processFiscalDocumentUseCase
                );

        this.jsonMapper =
                Objects.requireNonNull(
                        jsonMapper
                );
    }

    @RabbitListener(
            queues =
                    RabbitOutboxConfiguration
                            .FISCAL_DOCUMENT_QUEUE
    )
    public void onMessage(
            Message message
    ) throws Exception {

        String payload =
                new String(
                        message.getBody(),
                        StandardCharsets.UTF_8
                );

        DocumentReceivedMessage event =
                jsonMapper.readValue(
                        payload,
                        DocumentReceivedMessage.class
                );

        UUID documentId =
                UUID.fromString(
                        event.documentId()
                );

        processFiscalDocumentUseCase.process(
                new ProcessFiscalDocumentCommand(
                        documentId
                )
        );
    }
}