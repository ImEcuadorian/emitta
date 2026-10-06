package io.github.imecuadorian.emitta.accesskey.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessKeyTest {

    @Test
    void shouldRestoreValidAccessKey() {

        AccessKey accessKey =
                new AccessKey(
                        "2111202405176001321000110010010000001241234567810"
                );

        assertEquals(
                49,
                accessKey.value().length()
        );
    }

    @Test
    void shouldRejectIncorrectLength() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new AccessKey(
                        "123456789"
                )
        );
    }

    @Test
    void shouldRejectNonNumericValue() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new AccessKey(
                        "A111202405176001321000110010010000001241234567810"
                )
        );
    }

    @Test
    void shouldRejectInvalidCheckDigit() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new AccessKey(
                        "2111202405176001321000110010010000001241234567811"
                )
        );
    }
}