package io.github.imecuadorian.emitta.invoicexml.application.port.out;

public interface InvoiceXmlValidatorPort {

    void validate(
            byte[] xml
    );
}