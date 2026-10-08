package io.github.imecuadorian.emitta.sriauthorization.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SriAuthorizationModelsTest {

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    @Test
    void shouldAcceptValidAuthorizationRequest() {

        SriAuthorizationRequest request =
                new SriAuthorizationRequest(
                        FiscalEnvironment.TEST,
                        ACCESS_KEY
                );

        assertEquals(
                FiscalEnvironment.TEST,
                request.environment()
        );

        assertEquals(
                ACCESS_KEY,
                request.accessKey()
        );
    }

    @Test
    void shouldRejectInvalidAccessKey() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new SriAuthorizationRequest(
                        FiscalEnvironment.TEST,
                        "12345"
                )
        );
    }

    @Test
    void shouldRepresentAuthorizedDocument() {

        Instant authorizedAt =
                Instant.parse(
                        "2026-10-07T22:00:00Z"
                );

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.AUTHORIZED,
                        ACCESS_KEY,
                        authorizedAt,
                        "<factura/>",
                        List.of()
                );

        assertEquals(
                SriAuthorizationStatus.AUTHORIZED,
                result.status()
        );

        assertEquals(
                ACCESS_KEY,
                result.authorizationNumber()
        );

        assertEquals(
                authorizedAt,
                result.authorizedAt()
        );
    }

    @Test
    void shouldRepresentDocumentNotFound() {

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_FOUND,
                        null,
                        null,
                        null,
                        List.of()
                );

        assertEquals(
                SriAuthorizationStatus.NOT_FOUND,
                result.status()
        );

        assertNull(
                result.authorizationNumber()
        );
    }

    @Test
    void shouldRequireAuthorizationNumberWhenAuthorized() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new SriAuthorizationResult(
                        SriAuthorizationStatus.AUTHORIZED,
                        null,
                        null,
                        null,
                        List.of()
                )
        );
    }

    @Test
    void shouldProtectAuthorizationMessagesFromModification() {

        List<SriAuthorizationMessage> messages =
                new ArrayList<>();

        messages.add(
                new SriAuthorizationMessage(
                        "46",
                        "RUC no existe",
                        null,
                        "ERROR"
                )
        );

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_AUTHORIZED,
                        null,
                        null,
                        null,
                        messages
                );

        messages.clear();

        assertEquals(
                1,
                result.messages().size()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.messages().clear()
        );
    }
}