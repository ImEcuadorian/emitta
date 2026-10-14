package io.github.imecuadorian.emitta.document.application.service;
import io.github.imecuadorian.emitta.document.application.port.in.ReadDocumentUseCase;
import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;
import io.github.imecuadorian.emitta.document.application.exception.DocumentUnavailableException;
import io.github.imecuadorian.emitta.document.domain.Document;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public final class ReadDocumentService implements ReadDocumentUseCase {
    private final DocumentRepository documents;
    public ReadDocumentService(DocumentRepository documents) { this.documents=documents; }
    @Override public Document read(UUID tenantId,UUID documentId) {
        return documents.findById(documentId).filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(DocumentUnavailableException::new);
    }
}
