package io.github.imecuadorian.emitta.ride.application.service;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.StoreDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.application.model.StoreDocumentArtifactCommand;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.fiscalsigning.application.port.out.XmlSignatureVerifierPort;
import io.github.imecuadorian.emitta.ride.application.port.in.GenerateAndStoreInvoiceRideUseCase;
import io.github.imecuadorian.emitta.ride.application.port.out.AuthorizedInvoiceRideReaderPort;
import io.github.imecuadorian.emitta.ride.application.port.out.InvoiceRidePdfGeneratorPort;
import java.util.UUID;
/** Independent artifact operation: it never changes fiscal state or calls SRI. */
public final class GenerateAndStoreInvoiceRideService implements GenerateAndStoreInvoiceRideUseCase {
    private final DocumentRepository documents;
    private final LoadDocumentArtifactUseCase load;
    private final StoreDocumentArtifactUseCase store;
    private final XmlSignatureVerifierPort verifier;
    private final AuthorizedInvoiceRideReaderPort reader;
    private final InvoiceRidePdfGeneratorPort generator;
    public GenerateAndStoreInvoiceRideService(DocumentRepository documents, LoadDocumentArtifactUseCase load,
            StoreDocumentArtifactUseCase store, XmlSignatureVerifierPort verifier,
            AuthorizedInvoiceRideReaderPort reader, InvoiceRidePdfGeneratorPort generator) {
        this.documents=documents; this.load=load; this.store=store; this.verifier=verifier;
        this.reader=reader; this.generator=generator;
    }
    @Override public DocumentArtifact generate(UUID tenantId, UUID documentId) {
        var document=documents.findById(documentId).filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Document unavailable"));
        if (document.getStatus()!=DocumentStatus.AUTHORIZED || document.getAuthorizedAt()==null)
            throw new IllegalStateException("RIDE requires confirmed authorization");
        try {
            // Immutable existing PDF is reused; changed renderer output cannot overwrite it.
            return load.load(documentId, DocumentArtifactType.RIDE_PDF).artifact();
        } catch (io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactNotFoundException absent) {
            // First generation only. Integrity/storage failures must propagate.
        }
        byte[] xml=load.load(documentId, DocumentArtifactType.AUTHORIZED_XML).content();
        verifier.verify(xml);
        var request=reader.read(xml, document.getAccessKey(), document.getAuthorizedAt());
        return store.store(new StoreDocumentArtifactCommand(documentId, DocumentArtifactType.RIDE_PDF,
                "application/pdf", generator.generate(request)));
    }
}
