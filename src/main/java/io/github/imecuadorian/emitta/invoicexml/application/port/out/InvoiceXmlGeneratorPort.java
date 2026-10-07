package io.github.imecuadorian.emitta.invoicexml.application.port.out;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import io.github.imecuadorian.emitta.invoicexml.domain.GeneratedInvoiceXml;

public interface InvoiceXmlGeneratorPort {

    GeneratedInvoiceXml generate(
            InvoiceXmlData data
    );
}