package io.github.imecuadorian.emitta.outbox.application.port.out;

import io.github.imecuadorian.emitta.outbox.application.model.OutboxEvent;

public interface OutboxEventPort {

    void append(OutboxEvent event);
}