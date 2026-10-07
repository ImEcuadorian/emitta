package io.github.imecuadorian.emitta.invoicexml.application.port.in;

import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;

import java.util.UUID;

public interface GenerateAndStoreInvoiceXmlUseCase {

    DocumentArtifact generateAndStore(
            UUID documentId
    );
}