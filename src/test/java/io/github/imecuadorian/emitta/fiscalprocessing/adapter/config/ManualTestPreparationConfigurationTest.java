package io.github.imecuadorian.emitta.fiscalprocessing.adapter.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ManualTestPreparationConfigurationTest {
    private MockEnvironment safe() {
        var env = new MockEnvironment().withProperty("spring.main.web-application-type", "none")
                .withProperty("emitta.signing.enabled", "true").withProperty("emitta.artifacts.storage.enabled", "true");
        for (String flag : java.util.List.of("fiscal.worker", "outbox.publisher", "processing.jobs", "ride", "sri.reception", "sri.authorization"))
            env.setProperty("emitta." + flag + ".enabled", "false");
        return env;
    }
    @Test void restrictsDatabaseAndNoWebServer() {
        assertDoesNotThrow(() -> ManualTestPreparationConfiguration.requireSafeRuntime(safe(), "emitta_staging"));
        assertThrows(IllegalStateException.class, () -> ManualTestPreparationConfiguration.requireSafeRuntime(safe(), "emitta"));
        assertThrows(IllegalStateException.class, () -> ManualTestPreparationConfiguration.requireSafeRuntime(safe().withProperty("spring.main.web-application-type", "servlet"), "emitta_staging"));
    }
    @Test void rejectsAnyActiveOrUnspecifiedAutomaticFlag() {
        for (String flag : java.util.List.of("fiscal.worker", "outbox.publisher", "processing.jobs", "ride", "sri.reception", "sri.authorization")) {
            var env = safe().withProperty("emitta." + flag + ".enabled", "true");
            assertThrows(IllegalStateException.class, () -> ManualTestPreparationConfiguration.requireSafeRuntime(env, "emitta_staging"));
            assertThrows(IllegalStateException.class, () -> ManualTestPreparationConfiguration.rejectUnsafeFlagsBeforeBeans(env).postProcessBeanFactory(null));
        }
        assertThrows(IllegalStateException.class, () -> ManualTestPreparationConfiguration.requireSafeRuntime(new MockEnvironment(), "emitta_staging"));
    }
}
