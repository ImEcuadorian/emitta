package io.github.imecuadorian.emitta.invoicexml.domain;

import java.nio.charset.StandardCharsets;

public record GeneratedInvoiceXml(
        String content
) {

    public GeneratedInvoiceXml {

        if (content == null
                || content.isBlank()) {

            throw new IllegalArgumentException(
                    "Generated invoice XML cannot be blank"
            );
        }
    }

    public byte[] bytes() {
        return content.getBytes(
                StandardCharsets.UTF_8
        );
    }
}