package io.github.imecuadorian.emitta.document.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyKeyTest {

    @Test
    void shouldCreateValidKey() {

        IdempotencyKey key =
                new IdempotencyKey(
                        " order-2026-001 "
                );

        assertEquals(
                "order-2026-001",
                key.value()
        );
    }

    @Test
    void shouldRejectBlankKey() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new IdempotencyKey("   ")
        );
    }
}