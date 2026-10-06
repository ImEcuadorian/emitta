package io.github.imecuadorian.emitta.outbox.application.service;

import io.github.imecuadorian.emitta.outbox.application.model.PendingOutboxEvent;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxClaimPort;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublishPendingOutboxServiceTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T03:30:00Z"
            );

    @Mock
    private OutboxClaimPort outboxClaimPort;

    @Mock
    private OutboxMessagePublisher messagePublisher;

    private PublishPendingOutboxService service;

    @BeforeEach
    void setUp() {

        service =
                new PublishPendingOutboxService(
                        outboxClaimPort,
                        messagePublisher,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        ),
                        "worker-test",
                        50,
                        Duration.ofSeconds(30)
                );
    }

    @Test
    void shouldPublishAndMarkEvent() {

        PendingOutboxEvent event =
                event(1);

        when(
                outboxClaimPort.claimBatch(
                        anyString(),
                        anyInt(),
                        any()
                )
        ).thenReturn(
                List.of(event)
        );

        when(
                outboxClaimPort.markPublished(
                        any(),
                        anyString(),
                        any()
                )
        ).thenReturn(
                true
        );

        int published =
                service.publishBatch();

        assertEquals(
                1,
                published
        );

        verify(
                messagePublisher
        ).publish(
                event
        );

        verify(
                outboxClaimPort
        ).markPublished(
                eq(event.id()),
                eq("worker-test"),
                eq(NOW)
        );

        verify(
                outboxClaimPort,
                never()
        ).releaseAfterFailure(
                any(),
                anyString(),
                any(),
                anyString()
        );
    }

    @Test
    void shouldReleaseEventWhenPublicationFails() {

        PendingOutboxEvent event =
                event(1);

        when(
                outboxClaimPort.claimBatch(
                        anyString(),
                        anyInt(),
                        any()
                )
        ).thenReturn(
                List.of(event)
        );

        doThrow(
                new IllegalStateException(
                        "Rabbit unavailable"
                )
        ).when(
                messagePublisher
        ).publish(
                event
        );

        int published =
                service.publishBatch();

        assertEquals(
                0,
                published
        );

        verify(
                outboxClaimPort
        ).releaseAfterFailure(
                eq(event.id()),
                eq("worker-test"),
                eq(
                        NOW.plusSeconds(1)
                ),
                contains(
                        "Rabbit unavailable"
                )
        );

        verify(
                outboxClaimPort,
                never()
        ).markPublished(
                any(),
                anyString(),
                any()
        );
    }

    private PendingOutboxEvent event(
            int attempts
    ) {

        return new PendingOutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DOCUMENT",
                UUID.randomUUID(),
                "document.received.v1",
                """
                {
                  "status": "QUEUED"
                }
                """,
                attempts
        );
    }
}