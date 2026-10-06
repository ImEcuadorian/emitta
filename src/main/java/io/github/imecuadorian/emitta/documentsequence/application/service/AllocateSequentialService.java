package io.github.imecuadorian.emitta.documentsequence.application.service;

import io.github.imecuadorian.emitta.documentsequence.application.command.AllocateSequentialCommand;
import io.github.imecuadorian.emitta.documentsequence.application.exception.PointOfIssueInactiveException;
import io.github.imecuadorian.emitta.documentsequence.application.exception.PointOfIssueNotFoundException;
import io.github.imecuadorian.emitta.documentsequence.application.port.in.AllocateSequentialUseCase;
import io.github.imecuadorian.emitta.documentsequence.application.port.out.SequenceAllocationPort;
import io.github.imecuadorian.emitta.documentsequence.domain.SequenceScope;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupResult;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupUseCase;

import java.util.Objects;
import java.util.UUID;

public final class AllocateSequentialService
        implements AllocateSequentialUseCase {

    private final SequenceAllocationPort sequenceAllocationPort;
    private final PointOfIssueLookupUseCase pointOfIssueLookup;

    public AllocateSequentialService(
            SequenceAllocationPort sequenceAllocationPort,
            PointOfIssueLookupUseCase pointOfIssueLookup
    ) {
        this.sequenceAllocationPort =
                Objects.requireNonNull(sequenceAllocationPort);

        this.pointOfIssueLookup =
                Objects.requireNonNull(pointOfIssueLookup);
    }

    @Override
    public SequentialNumber allocate(
            AllocateSequentialCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Allocate sequential command cannot be null"
        );

        UUID pointOfIssueId =
                Objects.requireNonNull(
                        command.pointOfIssueId(),
                        "Point of issue id cannot be null"
                );

        Objects.requireNonNull(
                command.documentType(),
                "Document type cannot be null"
        );

        Objects.requireNonNull(
                command.environment(),
                "Fiscal environment cannot be null"
        );

        PointOfIssueLookupResult pointOfIssue =
                pointOfIssueLookup
                        .findById(pointOfIssueId)
                        .orElseThrow(
                                () ->
                                        new PointOfIssueNotFoundException(
                                                pointOfIssueId
                                        )
                        );

        if (!pointOfIssue.active()) {
            throw new PointOfIssueInactiveException(
                    pointOfIssueId
            );
        }

        SequenceScope scope =
                new SequenceScope(
                        pointOfIssueId,
                        command.documentType(),
                        command.environment()
                );

        return sequenceAllocationPort.allocateNext(
                scope
        );
    }
}