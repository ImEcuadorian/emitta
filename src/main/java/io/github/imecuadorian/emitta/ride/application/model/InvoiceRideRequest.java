package io.github.imecuadorian.emitta.ride.application.model;

import io.github.imecuadorian.emitta.invoicexml.application.model.InvoiceXmlData;
import java.time.Instant;
import java.util.Objects;

/**
 * Authorized fiscal snapshot to render as a human-readable RIDE.
 * The caller MUST obtain authorization details from a verified SRI response,
 * not from a client request. Do not expose this record as a public API DTO.
 */
public record InvoiceRideRequest(
        InvoiceXmlData invoice,
        String authorizationNumber,
        Instant authorizedAt
) {
    public InvoiceRideRequest {
        Objects.requireNonNull(invoice, "Invoice XML data is required");
        Objects.requireNonNull(authorizedAt, "SRI authorization timestamp is required");
        if (authorizationNumber == null || !authorizationNumber.matches("\\d{49}")) {
            throw new IllegalArgumentException("SRI authorization number must have 49 digits");
        }
        if (invoice.accessKey() == null || !invoice.accessKey().matches("\\d{49}")) {
            throw new IllegalArgumentException("Invoice access key must have 49 digits");
        }
        if (!authorizationNumber.equals(invoice.accessKey())) {
            throw new IllegalArgumentException("Authorization number must match the access key");
        }
    }
}
