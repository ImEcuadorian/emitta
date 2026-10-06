package io.github.imecuadorian.emitta.documentsequence.application.exception;

import java.util.UUID;

public final class PointOfIssueNotFoundException
        extends RuntimeException {

    private final UUID pointOfIssueId;

    public PointOfIssueNotFoundException(
            UUID pointOfIssueId
    ) {
        super(
                "Point of issue not found: "
                        + pointOfIssueId
        );

        this.pointOfIssueId = pointOfIssueId;
    }

    public UUID getPointOfIssueId() {
        return pointOfIssueId;
    }
}