package io.github.imecuadorian.emitta.invoicexml.adapter.out.xml;

import io.github.imecuadorian.emitta.invoicexml.application.exception.InvalidInvoiceXmlException;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SriInvoiceXsdValidatorTest {

    private final SriInvoiceXsdValidator validator =
            new SriInvoiceXsdValidator();

    @Test
    void shouldRejectInvalidInvoiceXml() {

        byte[] invalidXml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <factura id="comprobante" version="2.1.0">
                    <invalid>This is not an SRI invoice</invalid>
                </factura>
                """
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        assertThrows(
                InvalidInvoiceXmlException.class,
                () ->
                        validator.validate(
                                invalidXml
                        )
        );
    }
}