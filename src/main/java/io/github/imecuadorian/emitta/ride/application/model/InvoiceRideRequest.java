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
        Instant authorizedAt,
        java.util.Map<String,String> additionalInformation
) {
    public InvoiceRideRequest(InvoiceXmlData invoice, String authorizationNumber, Instant authorizedAt) {
        this(invoice,authorizationNumber,authorizedAt,java.util.Map.of());
    }
    public InvoiceRideRequest {
        additionalInformation=java.util.Map.copyOf(additionalInformation);
        if (invoice != null) {
            requireText(invoice.legalName(),"Issuer legal name");
            requireText(invoice.mainAddress(),"Issuer address");
            requireText(invoice.currency(),"Currency");
            if(invoice.ruc()==null || !invoice.ruc().matches("[0-9]{13}")) throw new IllegalArgumentException("Issuer RUC required");
            if(!java.util.Set.of("1","2").contains(invoice.environmentCode())) throw new IllegalArgumentException("Invalid environment");
            Objects.requireNonNull(invoice.issueDate(),"Issue date required");
            Objects.requireNonNull(invoice.buyer(),"Buyer required");
            requireText(invoice.buyer().name(),"Buyer name");
            requireText(invoice.buyer().identification(),"Buyer identification");
            if(invoice.items().isEmpty()) throw new IllegalArgumentException("Invoice items required");
            for(var item:invoice.items()) { requireText(item.code(),"Item code"); requireText(item.description(),"Item description"); }
        }

        Objects.requireNonNull(invoice, "Invoice XML data is required");
        Objects.requireNonNull(authorizedAt, "SRI authorization timestamp is required");
        if (authorizationNumber == null || !authorizationNumber.matches("[0-9]{49}")) {
            throw new IllegalArgumentException("SRI authorization number must have 49 digits");
        }
        if (invoice.accessKey() == null || !invoice.accessKey().matches("[0-9]{49}")) {
            throw new IllegalArgumentException("Invoice access key must have 49 digits");
        }
        if (!authorizationNumber.equals(invoice.accessKey())) {
            throw new IllegalArgumentException("Authorization number must match the access key");
        }
    }
    private static void requireText(String value,String field) {
        if(value==null || value.isBlank()) throw new IllegalArgumentException(field+" required");
    }
}
