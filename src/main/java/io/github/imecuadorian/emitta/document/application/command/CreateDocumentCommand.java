package io.github.imecuadorian.emitta.document.application.command;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.time.Instant;
import java.util.UUID;

public record CreateDocumentCommand(
        UUID tenantId,
        UUID pointOfIssueId,
        DocumentType documentType,
        FiscalEnvironment environment,
        String idempotencyKey,
        Instant issuedAt
) {
}