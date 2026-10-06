package io.github.imecuadorian.emitta.outbox.adapter.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitOutboxConfiguration {

    public static final String EVENTS_EXCHANGE =
            "emitta.events";

    public static final String FISCAL_DOCUMENT_QUEUE =
            "emitta.fiscal.documents";

    @Bean
    TopicExchange emittaEventsExchange() {

        return new TopicExchange(
                EVENTS_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    Queue fiscalDocumentQueue() {

        return QueueBuilder
                .durable(
                        FISCAL_DOCUMENT_QUEUE
                )
                .build();
    }

    @Bean
    Binding fiscalDocumentBinding(
            TopicExchange emittaEventsExchange,
            Queue fiscalDocumentQueue
    ) {

        return BindingBuilder
                .bind(
                        fiscalDocumentQueue
                )
                .to(
                        emittaEventsExchange
                )
                .with(
                        "document.received.v1"
                );
    }
}