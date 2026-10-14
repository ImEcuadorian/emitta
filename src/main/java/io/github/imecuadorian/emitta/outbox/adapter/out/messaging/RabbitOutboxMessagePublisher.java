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
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public RabbitOutboxMessagePublisher(
            RabbitTemplate rabbitTemplate
    ) {
        this(rabbitTemplate, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RabbitOutboxMessagePublisher(RabbitTemplate rabbitTemplate, io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics = metrics;
        var factory=rabbitTemplate.getConnectionFactory();
        if(factory!=null && (!factory.isPublisherConfirms() || factory.isSimplePublisherConfirms() || !factory.isPublisherReturns()))
            throw new IllegalStateException("Outbox requires correlated confirms and publisher returns");
        rabbitTemplate.setMandatory(true);
        this.rabbitTemplate =
                Objects.requireNonNull(
                        rabbitTemplate
                );
    }

    @Override public void publish(PendingOutboxEvent event) {
        publishMessage(RabbitOutboxConfiguration.EVENTS_EXCHANGE,event.eventType(),message(event),event.id().toString());
    }

    @Override public java.util.Map<java.util.UUID,RuntimeException> publishBatch(java.util.List<PendingOutboxEvent> events) {
        var pending=new java.util.LinkedHashMap<java.util.UUID,java.util.concurrent.CompletableFuture<Void>>();
        var failures=new java.util.LinkedHashMap<java.util.UUID,RuntimeException>();
        RuntimeException sendFailure=null;
        for(var event:events) {
            if(sendFailure!=null){failures.put(event.id(),sendFailure);continue;}
            try {pending.put(event.id(),sendConfirmed(RabbitOutboxConfiguration.EVENTS_EXCHANGE,event.eventType(),message(event),event.id().toString()));}
            catch(RuntimeException failure){failures.put(event.id(),failure);sendFailure=failure;}
        }
        for(var entry:pending.entrySet()) try {entry.getValue().join();}
        catch(RuntimeException failure){failures.put(entry.getKey(),failure);}
        return failures;
    }
    private Message message(
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

        properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        Message message =
                new Message(
                        event.payloadJson()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),
                        properties
                );

        return message;
    }

    public void publishMessage(String exchange,String routingKey,Message message,String messageId) {
        try {sendConfirmed(exchange,routingKey,message,messageId).get();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Rabbit publication interrupted",interrupted);}
        catch(java.util.concurrent.ExecutionException failed){throw new IllegalStateException("Rabbit publication not confirmed",failed);}
    }

    private java.util.concurrent.CompletableFuture<Void> sendConfirmed(String exchange,String routingKey,Message message,String messageId) {
        var sample=io.micrometer.core.instrument.Timer.start(metrics);
        String prefix=RabbitOutboxConfiguration.EVENTS_EXCHANGE.equals(exchange)?"emitta.outbox":"emitta.rabbit.dlq";
        message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        // A fresh correlation object is required even when a durable event is redelivered.
        var correlation=new CorrelationData(messageId+":"+java.util.UUID.randomUUID());
        try {
            rabbitTemplate.send(exchange,routingKey,message,correlation);
            return correlation.getFuture().orTimeout(CONFIRM_TIMEOUT_SECONDS,TimeUnit.SECONDS)
                .thenAccept(confirm->{
                    // Spring completes the confirm future after populating a mandatory return.
                    if(correlation.getReturned()!=null || !confirm.ack())
                        throw new IllegalStateException("Rabbit publication returned or nacked");
                    metrics.counter(prefix+".confirmed").increment();
                }).whenComplete((ignored,failure)->{
                    sample.stop(metrics.timer(prefix+".confirm.duration"));
                    if(failure!=null)metrics.counter(prefix+".confirm.failed").increment();
                });
        } catch(RuntimeException failure) {
            sample.stop(metrics.timer(prefix+".confirm.duration"));
            metrics.counter(prefix+".confirm.failed").increment();throw failure;
        }
    }
}
