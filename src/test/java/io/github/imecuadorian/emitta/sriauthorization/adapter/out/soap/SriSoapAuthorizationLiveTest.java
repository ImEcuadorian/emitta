
package io.github.imecuadorian.emitta.sriauthorization.adapter.out.soap;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SriSoapAuthorizationLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_SRI_LIVE_TEST",
            matches = "true"
    )
    void shouldParseRealSriInvalidAccessKeyResponse() {

        URI testEndpoint = URI.create(
                "https://celcer.sri.gob.ec/"
                        + "comprobantes-electronicos-ws/"
                        + "AutorizacionComprobantesOffline"
        );

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(15)
                        )
                        .build();

        SriSoapAuthorizationAdapter adapter =
                new SriSoapAuthorizationAdapter(
                        client,
                        testEndpoint,
                        testEndpoint,
                        Duration.ofSeconds(30)
                );

        SriAuthorizationRequest request =
                new SriAuthorizationRequest(
                        FiscalEnvironment.TEST,
                        "0".repeat(49)
                );

        var result = adapter.query(request);

        assertEquals(
                SriAuthorizationStatus.NOT_AUTHORIZED,
                result.status()
        );

        assertTrue(
                result.messages()
                        .stream()
                        .anyMatch(message ->
                                "80".equals(
                                        message.identifier()
                                )
                        ),
                "Expected SRI access key validation message"
        );
    }
}
