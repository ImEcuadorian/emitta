package io.github.imecuadorian.emitta.srireception.application.model;

import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SriReceptionModelTest {

    @Test
    void shouldDefensivelyCopySignedXml() {

        byte[] original =
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        SriReceptionRequest request =
                new SriReceptionRequest(
                        FiscalEnvironment.TEST,
                        original
                );

        original[0] =
                'X';

        assertArrayEquals(
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        ),
                request.signedXml()
        );

        byte[] returned =
                request.signedXml();

        returned[0] =
                'Y';

        assertArrayEquals(
                "<factura/>"
                        .getBytes(
                                StandardCharsets.UTF_8
                        ),
                request.signedXml()
        );
    }

    @Test
    void shouldRejectEmptySignedXml() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SriReceptionRequest(
                                FiscalEnvironment.TEST,
                                new byte[0]
                        )
        );
    }

    @Test
    void shouldCreateReceivedResultWithoutMessages() {

        SriReceptionResult result =
                new SriReceptionResult(
                        SriReceptionStatus.RECEIVED,
                        List.of()
                );

        assertEquals(
                SriReceptionStatus.RECEIVED,
                result.status()
        );

        assertEquals(
                List.of(),
                result.messages()
        );
    }

    @Test
    void shouldDefensivelyCopyReceptionMessages() {

        List<SriReceptionMessage> messages =
                new ArrayList<>();

        messages.add(
                new SriReceptionMessage(
                        "35",
                        "DOCUMENTO INVÁLIDO",
                        "Invalid XML structure",
                        "ERROR"
                )
        );

        SriReceptionResult result =
                new SriReceptionResult(
                        SriReceptionStatus.RETURNED,
                        messages
                );

        messages.clear();

        assertEquals(
                1,
                result.messages()
                        .size()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        result.messages()
                                .clear()
        );
    }
}