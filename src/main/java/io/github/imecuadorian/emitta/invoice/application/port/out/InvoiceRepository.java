package io.github.imecuadorian.emitta.invoice.application.port.out;

import io.github.imecuadorian.emitta.invoice.domain.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    void insert(
            Invoice invoice
    );

    Optional<Invoice> findByDocumentId(
            UUID documentId
    );
}