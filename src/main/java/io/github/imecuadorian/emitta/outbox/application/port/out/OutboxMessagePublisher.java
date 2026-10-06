package io.github.imecuadorian.emitta.outbox.application.port.out;

import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;

public interface OutboxMessagePublisher {

    void publish(
            PendingOutboxEvent event
    );
}