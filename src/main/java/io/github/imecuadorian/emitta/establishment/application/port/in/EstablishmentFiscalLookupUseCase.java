package io.github.imecuadorian.emitta.establishment.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface EstablishmentFiscalLookupUseCase {

    Optional<EstablishmentFiscalData> findFiscalDataById(
            UUID establishmentId
    );
}