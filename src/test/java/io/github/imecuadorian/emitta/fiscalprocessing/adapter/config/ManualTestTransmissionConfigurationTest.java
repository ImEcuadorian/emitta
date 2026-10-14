package io.github.imecuadorian.emitta.fiscalprocessing.adapter.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class ManualTestTransmissionConfigurationTest {
    @Test void blocksProductionAndAlternativeHostsAndQueries() {
        String path = "/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
        assertDoesNotThrow(() -> ManualTestTransmissionConfiguration.requireTestEndpoint(URI.create("https://celcer.sri.gob.ec" + path), "RecepcionComprobantesOffline"));
        for (String uri : new String[]{"https://cel.sri.gob.ec" + path, "http://celcer.sri.gob.ec" + path,
                "https://celcer.sri.gob.ec" + path + "?x=1", "https://example.com" + path})
            assertThrows(IllegalStateException.class, () -> ManualTestTransmissionConfiguration.requireTestEndpoint(URI.create(uri), "RecepcionComprobantesOffline"));
    }
    @Test void requiresExplicitApprovalAndDisabledWorkersBeforeBeansStart() {
        var env = new MockEnvironment().withProperty("spring.main.web-application-type", "none")
                .withProperty("emitta.manual.confirm-one-test-operation", "true");
        for (var flag : new String[]{"fiscal.worker", "outbox.publisher", "processing.jobs", "signing"}) env.setProperty("emitta." + flag + ".enabled", "false");
        assertDoesNotThrow(() -> ManualTestTransmissionConfiguration.requireSafeRuntime(env));
        env.setProperty("emitta.processing.jobs.enabled", "true");
        assertThrows(IllegalStateException.class, () -> ManualTestTransmissionConfiguration.requireSafeRuntime(env));
        env.setProperty("emitta.processing.jobs.enabled", "false");
        env.setProperty("emitta.manual.confirm-one-test-operation", "false");
        assertThrows(IllegalStateException.class, () -> ManualTestTransmissionConfiguration.requireSafeRuntime(env));
    }
}
