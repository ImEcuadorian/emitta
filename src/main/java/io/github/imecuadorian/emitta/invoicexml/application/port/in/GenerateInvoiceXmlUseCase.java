package io.github.imecuadorian.emitta.invoicexml.application.port.in;

import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;

import java.util.UUID;

public interface GenerateInvoiceXmlUseCase {

    GeneratedInvoiceXml generate(
            UUID documentId
    );
}