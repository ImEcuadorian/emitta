package io.github.imecuadorian.emitta.pointofissue.application.command;

import java.util.UUID;

public record CreatePointOfIssueCommand(
        UUID establishmentId,
        String code,
        String name
) {
}