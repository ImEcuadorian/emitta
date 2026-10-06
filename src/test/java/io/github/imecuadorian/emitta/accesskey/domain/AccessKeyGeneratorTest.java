package io.github.imecuadorian.emitta.accesskey.domain;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessKeyGeneratorTest {

    @Test
    void shouldGenerateOfficialSriAccessKeyExample() {

        AccessKeyComponents components =
                new AccessKeyComponents(
                        LocalDate.of(
                                2024,
                                11,
                                21
                        ),
                        DocumentType.DEBIT_NOTE,
                        "1760013210001",
                        FiscalEnvironment.TEST,
                        "001",
                        "001",
                        "000000124",
                        "12345678"
                );

        AccessKey accessKey =
                AccessKeyGenerator.generate(
                        components
                );

        assertEquals(
                "2111202405176001321000110010010000001241234567810",
                accessKey.value()
        );

        assertEquals(
                49,
                accessKey.value().length()
        );
    }
}