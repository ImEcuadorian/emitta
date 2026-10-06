package io.github.imecuadorian.emitta.pointofissue.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface PointOfIssueLookupUseCase {

    Optional<PointOfIssueLookupResult> findById(
            UUID pointOfIssueId
    );
}