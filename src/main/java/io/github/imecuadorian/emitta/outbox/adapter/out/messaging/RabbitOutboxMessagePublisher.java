package io.github.imecuadorian.emitta.outbox.adapter.out.messaging;

import io.github.imecuadorian.emitta.outbox.adapter.config.RabbitOutboxConfiguration;
import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxMessagePublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public final class RabbitOutboxMessagePublisher
        implements OutboxMessagePublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS =
            5L;

    private final RabbitTemplate rabbitTemplate;

    public RabbitOutboxMessagePublisher(
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate =
                Objects.requireNonNull(
                        rabbitTemplate
                );
    }

    @Override
    public void publish(
            PendingOutboxEvent event
    ) {

        Objects.requireNonNull(
                event,
                "Pending outbox event cannot be null"
        );

        MessageProperties properties =
                new MessageProperties();

        properties.setContentType(
                MessageProperties.CONTENT_TYPE_JSON
        );

        properties.setContentEncoding(
                StandardCharsets.UTF_8.name()
        );

        properties.setMessageId(
                event.id().toString()
        );

        properties.setType(
                event.eventType()
        );

        properties.setHeader(
                "x-emitta-event-id",
                event.id().toString()
        );

        properties.setHeader(
                "x-emitta-aggregate-type",
                event.aggregateType()
        );

        properties.setHeader(
                "x-emitta-aggregate-id",
                event.aggregateId().toString()
        );

        if (event.tenantId() != null) {
            properties.setHeader(
                    "x-emitta-tenant-id",
                    event.tenantId().toString()
            );
        }

        Message message =
                new Message(
                        event.payloadJson()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),
                        properties
                );

        CorrelationData correlationData =
                new CorrelationData(
                        event.id().toString()
                );

        rabbitTemplate.send(
                RabbitOutboxConfiguration.EVENTS_EXCHANGE,
                event.eventType(),
                message,
                correlationData
        );

        try {

            CorrelationData.Confirm confirm =
                    correlationData
                            .getFuture()
                            .get(
                                    CONFIRM_TIMEOUT_SECONDS,
                                    TimeUnit.SECONDS
                            );

            if (!confirm.ack()) {
                throw new IllegalStateException(
                        "RabbitMQ rejected outbox event "
                                + event.id()
                                + ": "
                                + confirm.reason()
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "RabbitMQ publication interrupted for event "
                            + event.id(),
                    exception
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "RabbitMQ publication failed for event "
                            + event.id(),
                    exception
            );
        }
    }
}