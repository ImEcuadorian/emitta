package io.github.imecuadorian.emitta.outbox.adapter.config;

import org.junit.jupiter.api.Test;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxClaimPort;
import io.github.imecuadorian.emitta.outbox.application.port.out.OutboxMessagePublisher;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxLeasePolicyTest {
    @Test void leaseMustCoverWorstCaseConfirmBatchBeforeAnyPublication() {
        var config=new OutboxPublisherConfiguration();
        var claims=mock(OutboxClaimPort.class);var publisher=mock(OutboxMessagePublisher.class);
        assertThrows(IllegalArgumentException.class,()->config.publishPendingOutboxUseCase(claims,publisher,
            Clock.systemUTC(),Duration.ofSeconds(1),Duration.ofMinutes(1),50,Duration.ofSeconds(30)));
        assertNotNull(config.publishPendingOutboxUseCase(claims,publisher,
            Clock.systemUTC(),Duration.ofSeconds(1),Duration.ofMinutes(1),50,Duration.ofMinutes(5)));
        verifyNoInteractions(claims,publisher);
    }
}
