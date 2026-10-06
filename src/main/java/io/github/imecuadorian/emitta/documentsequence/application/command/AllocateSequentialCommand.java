package io.github.imecuadorian.emitta.documentsequence.application.command;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.util.UUID;

public record AllocateSequentialCommand(
        UUID pointOfIssueId,
        DocumentType documentType,
        FiscalEnvironment environment
) {
}