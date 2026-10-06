package io.github.imecuadorian.emitta.accesskey.adapter.out.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureRandomNumericCodeGeneratorTest {

    @Test
    void shouldAlwaysGenerateEightNumericDigits() {

        SecureRandomNumericCodeGenerator generator =
                new SecureRandomNumericCodeGenerator();

        for (int i = 0; i < 100; i++) {

            String code =
                    generator.generate();

            assertTrue(
                    code.matches("\\d{8}")
            );
        }
    }
}