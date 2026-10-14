package io.github.imecuadorian.emitta.ride.application.port.in;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifact;
import java.util.UUID;
public interface GenerateAndStoreInvoiceRideUseCase {
    DocumentArtifact generate(UUID tenantId, UUID documentId);
}
