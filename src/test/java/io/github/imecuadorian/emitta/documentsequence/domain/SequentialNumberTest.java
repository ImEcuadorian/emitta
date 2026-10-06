package io.github.imecuadorian.emitta.documentsequence.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequentialNumberTest {

    @Test
    void shouldFormatSequentialWithNineDigits() {

        SequentialNumber sequential =
                new SequentialNumber(1);

        assertEquals(
                "000000001",
                sequential.formatted()
        );
    }

    @Test
    void shouldFormatLargerSequential() {

        SequentialNumber sequential =
                new SequentialNumber(12345);

        assertEquals(
                "000012345",
                sequential.formatted()
        );
    }

    @Test
    void shouldRejectZero() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new SequentialNumber(0)
        );
    }

    @Test
    void shouldRejectValueAboveMaximum() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new SequentialNumber(
                        1_000_000_000L
                )
        );
    }
}