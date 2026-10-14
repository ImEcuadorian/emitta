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
    @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.batch-size:50}")
    private int admissionBatchSize=50;
    @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.receive-timeout-ms:100}")
    private long admissionReceiveTimeout=100;
    @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.concurrency:1}")
    private int admissionConcurrency=1;

    public static final String EVENTS_EXCHANGE =
            "emitta.events";

    public static final String FISCAL_DOCUMENT_QUEUE =
            "emitta.fiscal.documents";

    public static final String DEAD_LETTER_EXCHANGE="emitta.deadletters";
    public static final String DEAD_LETTER_QUEUE="emitta.processing.dlq";

    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory fiscalDurableListenerFactory(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory, int retries,
            java.time.Duration initialDelay, java.time.Duration maxDelay) {
        return fiscalDurableListenerFactory(connectionFactory,retries,initialDelay,maxDelay,
                java.time.Duration.ZERO,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory fiscalDurableListenerFactory(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.retries:2}") int retries,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.initial-delay:PT1S}") java.time.Duration initialDelay,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.max-delay:PT5S}") java.time.Duration maxDelay,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.outage-cooldown:PT30S}") java.time.Duration cooldown,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        return fiscalDurableListenerFactory(connectionFactory,retries,initialDelay,maxDelay,cooldown,metrics,null);
    }

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory fiscalDurableListenerFactory(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.retries:2}") int retries,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.initial-delay:PT1S}") java.time.Duration initialDelay,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.max-delay:PT5S}") java.time.Duration maxDelay,
            @org.springframework.beans.factory.annotation.Value("${emitta.processing.admission.outage-cooldown:PT30S}") java.time.Duration cooldown,
            io.micrometer.core.instrument.MeterRegistry metrics,
            io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher publisher) {
        if (retries < 0 || retries > 5 || initialDelay.isNegative() || initialDelay.isZero() || maxDelay.compareTo(initialDelay) < 0)
            throw new IllegalArgumentException("Invalid durable admission retry policy");
        if(cooldown.isNegative() || cooldown.compareTo(java.time.Duration.ofMinutes(1))>0)
            throw new IllegalArgumentException("Invalid admission outage cooldown");
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        if(admissionBatchSize<1 || admissionBatchSize>50 || admissionReceiveTimeout<1 || admissionReceiveTimeout>1000)
            throw new IllegalArgumentException("Invalid bounded admission batch");
        if(admissionConcurrency<1 || admissionConcurrency>2)throw new IllegalArgumentException("Admission concurrency must be 1 or 2 within the connection budget");
        factory.setConcurrentConsumers(admissionConcurrency);
        factory.setMaxConcurrentConsumers(admissionConcurrency);
        factory.setPrefetchCount(admissionBatchSize);
        factory.setBatchSize(admissionBatchSize);
        factory.setConsumerBatchEnabled(true);
        factory.setReceiveTimeout(admissionReceiveTimeout);
        factory.setBatchReceiveTimeout(admissionReceiveTimeout);
        factory.setDefaultRequeueRejected(true);
        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder.stateless()
                .maxRetries(retries).backOffOptions(initialDelay.toMillis(), 2, maxDelay.toMillis())
                .recoverer((org.springframework.amqp.rabbit.retry.MessageBatchRecoverer)(messages, failure) -> {
                    metrics.counter("emitta.rabbit.admission.exhausted").increment();
                    if(publisher!=null) {
                        try {
                            String category=io.github.imecuadorian.emitta.recovery.WorkFailureClassifier.classify(failure).name();
                            for(var original:messages) {
                                String id=original.getMessageProperties().getMessageId();
                                if(id==null)id=java.util.UUID.randomUUID().toString();
                                Object previous=original.getMessageProperties().getHeader("x-emitta-admission-attempts");
                                long count=previous instanceof Number number?Math.max(0,number.longValue()):0;
                                long attempts=Math.min(Integer.MAX_VALUE,count)+retries+1L;
                                var properties=org.springframework.amqp.core.MessagePropertiesBuilder
                                    .fromClonedProperties(original.getMessageProperties()).setMessageId(id)
                                    .setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT)
                                    .setHeader("x-emitta-failure-category",category)
                                    .setHeader("x-emitta-admission-exhausted",true)
                                    .setHeader("x-emitta-admission-attempts",Math.min(Integer.MAX_VALUE,attempts)).build();
                                publisher.publishMessage(DEAD_LETTER_EXCHANGE,"fiscal.admission.exhausted",
                                    new org.springframework.amqp.core.Message(original.getBody(),properties),id);
                            }
                            // AUTO ACK is allowed only after every quarantine publication was routed and confirmed.
                            metrics.counter("emitta.rabbit.admission.quarantined").increment(messages.size());
                            return;
                        } catch(RuntimeException unavailable) {
                            metrics.counter("emitta.rabbit.admission.quarantine.failed").increment();
                        }
                    }
                    // No confirmed quarantine: keep the original delivery, including unknown confirm outcomes.
                    try { Thread.sleep(cooldown); }
                    catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    throw new org.springframework.amqp.ImmediateRequeueAmqpException("Durable admission unavailable");
                }).build());
        return factory;
    }

    @Bean TopicExchange deadLetterExchange() { return new TopicExchange(DEAD_LETTER_EXCHANGE,true,false); }
    @Bean Queue deadLetterQueue() { return QueueBuilder.durable(DEAD_LETTER_QUEUE).build(); }
    @Bean Binding deadLetterBinding(TopicExchange deadLetterExchange,Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
    }
    @Bean Binding failedWorkBinding(TopicExchange emittaEventsExchange,Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue).to(emittaEventsExchange).with("processing.failed.v1");
    }

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
