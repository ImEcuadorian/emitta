package io.github.imecuadorian.emitta.pointofissue.application.service;

import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupResult;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.out.PointOfIssueRepository;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PointOfIssueLookupService
        implements PointOfIssueLookupUseCase {

    private final PointOfIssueRepository repository;

    public PointOfIssueLookupService(
            PointOfIssueRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(repository);
    }

    @Override
    public Optional<PointOfIssueLookupResult> findById(
            UUID pointOfIssueId
    ) {
        Objects.requireNonNull(
                pointOfIssueId,
                "Point of issue id cannot be null"
        );

        return repository
                .findById(pointOfIssueId)
                .map(pointOfIssue ->
                        new PointOfIssueLookupResult(
                                pointOfIssue.getId(),
                                pointOfIssue.getStatus()
                                        == PointOfIssueStatus.ACTIVE
                        )
                );
    }
}