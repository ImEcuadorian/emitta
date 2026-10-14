package io.github.imecuadorian.emitta.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RecoveryCommandConfigurationTest {
    @Test
    void unsafeFlagsAndMixedTargetsAreRejectedBeforeAnyDurableOperation() {
        var work = mock(DurableWorkPort.class);
        var context = mock(ConfigurableApplicationContext.class);
        var config = new RecoveryCommandConfiguration();
        var arguments = new DefaultApplicationArguments();
        for (String flag : java.util.List.of("emitta.fiscal.worker.enabled", "emitta.outbox.publisher.enabled",
                "emitta.sri.reception.enabled", "emitta.sri.authorization.enabled", "emitta.processing.jobs.enabled")) {
            var environment = new MockEnvironment().withProperty(flag, "true");
            assertThrows(IllegalStateException.class,
                    () -> config.recoverProcessingJob(work, environment, context).run(arguments));
        }
        var environment = new MockEnvironment()
                .withProperty("emitta.recovery.tenant-id", java.util.UUID.randomUUID().toString())
                .withProperty("emitta.recovery.event-id", java.util.UUID.randomUUID().toString())
                .withProperty("emitta.recovery.job-id", java.util.UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,
                () -> config.recoverProcessingJob(work, environment, context).run(arguments));
        for (String target : java.util.List.of("emitta.recovery.document-id", "emitta.recovery.event-id")) {
            var incomplete = new MockEnvironment()
                    .withProperty("emitta.recovery.tenant-id", java.util.UUID.randomUUID().toString())
                    .withProperty(target, java.util.UUID.randomUUID().toString());
            assertThrows(IllegalArgumentException.class,
                    () -> config.recoverProcessingJob(work, incomplete, context).run(arguments));
            incomplete.withProperty("emitta.recovery.job-id", java.util.UUID.randomUUID().toString());
            assertThrows(IllegalArgumentException.class,
                    () -> config.recoverProcessingJob(work, incomplete, context).run(arguments));
        }
        verifyNoInteractions(work, context);
    }
}
