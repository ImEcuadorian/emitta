package io.github.imecuadorian.emitta.accesskey.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Modulus11Test {

    @Test
    void shouldCalculateOfficialSriExample() {

        int result =
                Modulus11.calculate(
                        "41261533"
                );

        assertEquals(
                6,
                result
        );
    }
}