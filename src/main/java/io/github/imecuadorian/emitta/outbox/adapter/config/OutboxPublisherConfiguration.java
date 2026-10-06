package io.github.imecuadorian.emitta.outbox.adapter.config;

import io.github.imecuadorian.emitta.outbox.application.port.in.PublishPendingOutboxUseCase;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxClaimPort;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxMessagePublisher;
import io.github.imecuadorian.emitta.outbox.application.service.PublishPendingOutboxService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration
public class OutboxPublisherConfiguration {

    @Bean
    PublishPendingOutboxUseCase publishPendingOutboxUseCase(
            OutboxClaimPort outboxClaimPort,
            OutboxMessagePublisher messagePublisher,
            Clock clock
    ) {

        String workerId =
                "emitta-"
                        + UUID.randomUUID();

        return new PublishPendingOutboxService(
                outboxClaimPort,
                messagePublisher,
                clock,
                workerId,
                50,
                Duration.ofSeconds(30)
        );
    }
}