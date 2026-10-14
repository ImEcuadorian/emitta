package io.github.imecuadorian.emitta.document.application.port.in;
import io.github.imecuadorian.emitta.document.domain.Document;
import java.util.UUID;
public interface ReadDocumentUseCase { Document read(UUID tenantId, UUID documentId); }
