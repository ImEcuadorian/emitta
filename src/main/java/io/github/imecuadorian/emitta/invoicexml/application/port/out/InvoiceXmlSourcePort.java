package io.github.imecuadorian.emitta.invoicexml.application.port.out;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlSourceData;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceXmlSourcePort {

    Optional<InvoiceXmlSourceData> findByDocumentId(
            UUID documentId
    );
}