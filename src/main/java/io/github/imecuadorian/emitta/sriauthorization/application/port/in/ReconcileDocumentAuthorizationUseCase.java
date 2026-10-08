package io.github.imecuadorian.emitta.sriauthorization.application.port.in;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;

import java.util.UUID;

public interface ReconcileDocumentAuthorizationUseCase {

    SriAuthorizationResult reconcile(UUID documentId);
}