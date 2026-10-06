package io.github.imecuadorian.emitta.establishment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstablishmentCodeTest {

    @Test
    void shouldCreateValidCode() {

        EstablishmentCode code =
                new EstablishmentCode("001");

        assertEquals(
                "001",
                code.value()
        );
    }

    @Test
    void shouldNormalizeCode() {

        EstablishmentCode code =
                new EstablishmentCode(" 001 ");

        assertEquals(
                "001",
                code.value()
        );
    }

    @Test
    void shouldRejectShortCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new EstablishmentCode("01")
        );
    }

    @Test
    void shouldRejectNonNumericCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new EstablishmentCode("A01")
        );
    }
}