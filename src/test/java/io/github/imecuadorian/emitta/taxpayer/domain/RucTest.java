package io.github.imecuadorian.emitta.taxpayer.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RucTest {

    @Test
    void shouldCreateValidRuc() {

        Ruc ruc =
                new Ruc("1790012345001");

        assertEquals(
                "1790012345001",
                ruc.value()
        );
    }

    @Test
    void shouldNormalizeRuc() {

        Ruc ruc =
                new Ruc(" 1790012345001 ");

        assertEquals(
                "1790012345001",
                ruc.value()
        );
    }

    @Test
    void shouldRejectShortRuc() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Ruc("179001")
        );
    }

    @Test
    void shouldRejectNonNumericRuc() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Ruc(
                        "17900123450AB"
                )
        );
    }
}