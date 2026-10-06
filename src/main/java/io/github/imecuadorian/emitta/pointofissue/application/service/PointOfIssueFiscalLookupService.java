package io.github.imecuadorian.emitta.pointofissue.application.service;

import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalData;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.out.PointOfIssueRepository;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PointOfIssueFiscalLookupService
        implements PointOfIssueFiscalLookupUseCase {

    private final PointOfIssueRepository repository;

    public PointOfIssueFiscalLookupService(
            PointOfIssueRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(repository);
    }

    @Override
    public Optional<PointOfIssueFiscalData> findFiscalDataById(
            UUID pointOfIssueId
    ) {

        Objects.requireNonNull(
                pointOfIssueId,
                "Point of issue id cannot be null"
        );

        return repository
                .findById(pointOfIssueId)
                .map(pointOfIssue ->
                        new PointOfIssueFiscalData(
                                pointOfIssue.getId(),
                                pointOfIssue.getEstablishmentId(),
                                pointOfIssue.getCode().value(),
                                pointOfIssue.getStatus()
                                        == PointOfIssueStatus.ACTIVE
                        )
                );
    }
}