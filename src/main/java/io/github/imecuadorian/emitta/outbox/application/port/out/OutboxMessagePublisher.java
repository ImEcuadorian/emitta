package io.github.imecuadorian.emitta.outbox.application.port.out;

import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;

public interface OutboxMessagePublisher {
    /** Missing IDs succeeded; every failure stays eligible for durable recovery. */
    default java.util.Map<java.util.UUID,RuntimeException> publishBatch(java.util.List<PendingOutboxEvent> events) {
        var failures=new java.util.LinkedHashMap<java.util.UUID,RuntimeException>();
        for(var event:events) try {publish(event);} catch(RuntimeException failure){failures.put(event.id(),failure);}
        return failures;
    }

    void publish(
            PendingOutboxEvent event
    );
}
