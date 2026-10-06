package io.github.imecuadorian.emitta.documentsequence.application.exception;

import java.util.UUID;

public final class PointOfIssueInactiveException
        extends RuntimeException {

    private final UUID pointOfIssueId;

    public PointOfIssueInactiveException(
            UUID pointOfIssueId
    ) {
        super(
                "Point of issue is inactive: "
                        + pointOfIssueId
        );

        this.pointOfIssueId = pointOfIssueId;
    }

    public UUID getPointOfIssueId() {
        return pointOfIssueId;
    }
}