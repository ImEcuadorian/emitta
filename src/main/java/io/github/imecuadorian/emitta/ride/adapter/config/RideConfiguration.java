package io.github.imecuadorian.emitta.ride.adapter.config;

import io.github.imecuadorian.emitta.ride.adapter.out.pdf.PdfBoxInvoiceRideGenerator;
import io.github.imecuadorian.emitta.ride.application.port.out.InvoiceRidePdfGeneratorPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RideConfiguration {
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="emitta.ride.enabled", havingValue="true")
    io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase generateAndStoreInvoiceRideUseCase(
            io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository documents,
            io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase load,
            io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase store,
            org.springframework.beans.factory.ObjectProvider<io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort> verifier,
            InvoiceRidePdfGeneratorPort generator) {
        return new io.github.imecuadorian.emitta.ride.application.service.GenerateAndStoreInvoiceRideService(
                documents, load, store, verifier.getIfAvailable(io.github.imecuadorian.emitta.fiscalsigning.adapter.out.xml.JdkSriXadesSignatureVerifier::new),
                new io.github.imecuadorian.emitta.ride.adapter.out.xml.AuthorizedInvoiceRideReader(), generator);
    }
    @Bean
    InvoiceRidePdfGeneratorPort invoiceRidePdfGeneratorPort() {
        return new PdfBoxInvoiceRideGenerator();
    }
}
