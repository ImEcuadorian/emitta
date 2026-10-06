package io.github.imecuadorian.emitta.pointofissue.adapter.in.web;

import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;

import java.time.Instant;
import java.util.UUID;

public record PointOfIssueResponse(
        UUID id,
        UUID establishmentId,
        String code,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static PointOfIssueResponse from(
            PointOfIssue pointOfIssue
    ) {
        return new PointOfIssueResponse(
                pointOfIssue.getId(),
                pointOfIssue.getEstablishmentId(),
                pointOfIssue.getCode().value(),
                pointOfIssue.getName(),
                pointOfIssue.getStatus().name(),
                pointOfIssue.getCreatedAt(),
                pointOfIssue.getUpdatedAt()
        );
    }
}