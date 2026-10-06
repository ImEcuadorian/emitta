package io.github.imecuadorian.emitta.outbox.adapter.in.scheduling;

import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class OutboxPublisherSchedulerTest {

    @Test
    void shouldStopWhenThereAreNoMoreEvents() {

        PublishPendingOutboxUseCase useCase =
                mock(
                        PublishPendingOutboxUseCase.class
                );

        when(
                useCase.publishBatch()
        ).thenReturn(
                50,
                20,
                0
        );

        OutboxPublisherScheduler scheduler =
                new OutboxPublisherScheduler(
                        useCase
                );

        scheduler.publishPending();

        verify(
                useCase,
                times(3)
        ).publishBatch();
    }

    @Test
    void shouldStopImmediatelyWhenOutboxIsEmpty() {

        PublishPendingOutboxUseCase useCase =
                mock(
                        PublishPendingOutboxUseCase.class
                );

        when(
                useCase.publishBatch()
        ).thenReturn(
                0
        );

        OutboxPublisherScheduler scheduler =
                new OutboxPublisherScheduler(
                        useCase
                );

        scheduler.publishPending();

        verify(
                useCase,
                times(1)
        ).publishBatch();
    }

    @Test
    void shouldLimitNumberOfBatchesPerRun() {

        PublishPendingOutboxUseCase useCase =
                mock(
                        PublishPendingOutboxUseCase.class
                );

        when(
                useCase.publishBatch()
        ).thenReturn(
                50
        );

        OutboxPublisherScheduler scheduler =
                new OutboxPublisherScheduler(
                        useCase
                );

        scheduler.publishPending();

        verify(
                useCase,
                times(10)
        ).publishBatch();
    }
}