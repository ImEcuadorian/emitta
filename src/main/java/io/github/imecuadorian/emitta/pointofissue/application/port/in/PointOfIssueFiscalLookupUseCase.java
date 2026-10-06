package io.github.imecuadorian.emitta.pointofissue.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface PointOfIssueFiscalLookupUseCase {

    Optional<PointOfIssueFiscalData> findFiscalDataById(
            UUID pointOfIssueId
    );
}