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
    @org.springframework.beans.factory.annotation.Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionWaitMillis=30000;

    @Bean
    PublishPendingOutboxUseCase publishPendingOutboxUseCase(
            OutboxClaimPort outboxClaimPort,
            OutboxMessagePublisher messagePublisher,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${emitta.outbox.publisher.initial-backoff:PT1S}") Duration initialBackoff,
            @org.springframework.beans.factory.annotation.Value("${emitta.outbox.publisher.maximum-backoff:PT1M}") Duration maximumBackoff,
            @org.springframework.beans.factory.annotation.Value("${emitta.outbox.publisher.batch-size:50}") int batchSize,
            @org.springframework.beans.factory.annotation.Value("${emitta.outbox.publisher.claim-lease:PT2M}") Duration claimLease
    ) {

        // Correlated confirms overlap; reserve budget for claim/mark connection waits and DB effects.
        if(connectionWaitMillis<1 || batchSize<1 || batchSize>50 ||
            claimLease.compareTo(Duration.ofMillis(Math.addExact(Math.multiplyExact(connectionWaitMillis,2),30000)))<0)
            throw new IllegalArgumentException("Outbox lease must cover the bounded confirm batch plus persistence margin");
        String workerId =
                "emitta-"
                        + UUID.randomUUID();

        return new PublishPendingOutboxService(
                outboxClaimPort,
                messagePublisher,
                clock,
                workerId,
                batchSize,
                claimLease, initialBackoff, maximumBackoff, true
        );
    }
}
