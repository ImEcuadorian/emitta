package io.github.imecuadorian.emitta.pointofissue.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointOfIssueCodeTest {

    @Test
    void shouldCreateValidCode() {

        PointOfIssueCode code =
                new PointOfIssueCode("001");

        assertEquals(
                "001",
                code.value()
        );
    }

    @Test
    void shouldNormalizeCode() {

        PointOfIssueCode code =
                new PointOfIssueCode(" 002 ");

        assertEquals(
                "002",
                code.value()
        );
    }

    @Test
    void shouldRejectShortCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PointOfIssueCode("01")
        );
    }

    @Test
    void shouldRejectNonNumericCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PointOfIssueCode("A01")
        );
    }
}