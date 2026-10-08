package io.github.imecuadorian.emitta.sriauthorization.application.service;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;
import io.github.imecuadorian.emitta.sriauthorization.domain.SriAuthorizationStatus;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerifyAuthorizedXmlServiceTest {

    private static final String ACCESS_KEY =
            "2111202405176001321000110010010000001241234567810";

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <factura id="comprobante" version="2.1.0">
              <infoTributaria>
                <claveAcceso>%s</claveAcceso>
              </infoTributaria>
            </factura>
            """.formatted(ACCESS_KEY);

    private final VerifyAuthorizedXmlService service =
            new VerifyAuthorizedXmlService();

    @Test
    void shouldAcceptIdenticalAuthorizedXml() {

        assertDoesNotThrow(
                () -> service.verify(
                        XML.getBytes(StandardCharsets.UTF_8),
                        authorizedResult(XML)
                )
        );
    }

    @Test
    void shouldRejectModifiedAuthorizedXml() {

        String modifiedXml =
                XML.replace(
                        "version=\"2.1.0\"",
                        "version=\"1.0.0\""
                );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.verify(
                                XML.getBytes(StandardCharsets.UTF_8),
                                authorizedResult(modifiedXml)
                        )
                );

        assertEquals(
                "SRI authorized XML differs from the original signed XML",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDifferentAccessKeyEvenWithValidXml() {

        String modifiedXml =
                XML.replace(
                        ACCESS_KEY,
                        "0".repeat(49)
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.verify(
                        XML.getBytes(StandardCharsets.UTF_8),
                        authorizedResult(modifiedXml)
                )
        );
    }

    @Test
    void shouldRejectNotAuthorizedResponse() {

        SriAuthorizationResult result =
                new SriAuthorizationResult(
                        SriAuthorizationStatus.NOT_AUTHORIZED,
                        null,
                        null,
                        null,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.verify(
                        XML.getBytes(StandardCharsets.UTF_8),
                        result
                )
        );
    }

    @Test
    void shouldRejectEmptyOriginalXml() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.verify(
                        new byte[0],
                        authorizedResult(XML)
                )
        );
    }

    @Test
    void shouldRejectMissingReturnedXml() {

        SriAuthorizationResult result =
                authorizedResult(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.verify(
                        XML.getBytes(StandardCharsets.UTF_8),
                        result
                )
        );
    }

    private SriAuthorizationResult authorizedResult(
            String xml
    ) {

        return new SriAuthorizationResult(
                SriAuthorizationStatus.AUTHORIZED,
                ACCESS_KEY,
                Instant.parse("2026-10-08T01:00:00Z"),
                xml,
                List.of()
        );
    }
}