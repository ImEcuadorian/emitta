package io.github.imecuadorian.emitta.accesskey.application.command;

import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateAccessKeyCommand(
        UUID pointOfIssueId,
        DocumentType documentType,
        FiscalEnvironment environment,
        LocalDate issueDate
) {
}