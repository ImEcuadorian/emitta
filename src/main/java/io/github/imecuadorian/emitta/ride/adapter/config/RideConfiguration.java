package io.github.imecuadorian.emitta.ride.adapter.config;

import io.github.imecuadorian.emitta.ride.adapter.out.pdf.PdfBoxInvoiceRideGenerator;
import io.github.imecuadorian.emitta.ride.application.port.out.InvoiceRidePdfGeneratorPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RideConfiguration {
    @Bean
    InvoiceRidePdfGeneratorPort invoiceRidePdfGeneratorPort() {
        return new PdfBoxInvoiceRideGenerator();
    }
}
