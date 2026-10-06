package io.github.imecuadorian.emitta.outbox.application.port.in;

public interface PublishPendingOutboxUseCase {

    int publishBatch();
}