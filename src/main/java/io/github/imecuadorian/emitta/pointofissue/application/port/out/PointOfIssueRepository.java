package io.github.imecuadorian.emitta.pointofissue.application.port.out;

import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;

import java.util.Optional;
import java.util.UUID;

public interface PointOfIssueRepository {

    PointOfIssue save(PointOfIssue pointOfIssue);

    Optional<PointOfIssue> findById(UUID id);

    boolean existsById(UUID id);

    boolean existsByEstablishmentIdAndCode(
            UUID establishmentId,
            PointOfIssueCode code
    );
}