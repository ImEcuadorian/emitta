package io.github.imecuadorian.emitta.outbox.adapter.config;

import io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher;
import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelatedOutboxBatchTest {
    @Test void unsafeConfirmOrReturnConfigurationIsRejectedBeforePublication() {
        var template=mock(RabbitTemplate.class);var factory=mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        assertThrows(IllegalStateException.class,()->new RabbitOutboxMessagePublisher(template));
        when(factory.isPublisherConfirms()).thenReturn(true);
        assertThrows(IllegalStateException.class,()->new RabbitOutboxMessagePublisher(template));
        when(factory.isPublisherReturns()).thenReturn(true);
        assertDoesNotThrow(()->new RabbitOutboxMessagePublisher(template));
        verify(template,never()).send(anyString(),anyString(),any(),any(CorrelationData.class));
    }
    @Test void batchSendsBeforeWaitingAndCorrelatesOutOfOrderAckAndNack() throws Exception {
        var template=mock(RabbitTemplate.class);
        var correlations=new CopyOnWriteArrayList<CorrelationData>();
        doAnswer(call->{correlations.add(call.getArgument(3));return null;}).when(template).send(anyString(),anyString(),any(),any(CorrelationData.class));
        var publisher=new RabbitOutboxMessagePublisher(template);
        var events=List.of(event(),event(),event());
        try(var executor=Executors.newSingleThreadExecutor()) {
            var result=executor.submit(()->publisher.publishBatch(events));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2)).until(()->correlations.size()==3);
            assertFalse(result.isDone());
            correlations.get(2).getFuture().complete(new CorrelationData.Confirm(true,null));
            correlations.get(0).getFuture().complete(new CorrelationData.Confirm(true,null));
            assertFalse(result.isDone());
            correlations.get(1).getFuture().complete(new CorrelationData.Confirm(false,"synthetic nack"));
            assertEquals(Set.of(events.get(1).id()),result.get(2,TimeUnit.SECONDS).keySet());
            assertEquals(3,correlations.stream().map(CorrelationData::getId).distinct().count());
        }
    }
    @Test void failedSendStopsFurtherConnectionAttemptsAndLeavesAllEventsFailed() {
        var template=mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("synthetic disconnected")).when(template).send(anyString(),anyString(),any(),any(CorrelationData.class));
        var result=new RabbitOutboxMessagePublisher(template).publishBatch(List.of(event(),event(),event()));
        assertEquals(3,result.size());verify(template,times(1)).send(anyString(),anyString(),any(),any(CorrelationData.class));
    }
    private PendingOutboxEvent event(){return new PendingOutboxEvent(UUID.randomUUID(),UUID.randomUUID(),"DOCUMENT",UUID.randomUUID(),"document.received.v1","{}",1);}
}
