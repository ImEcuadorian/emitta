package io.github.imecuadorian.emitta.taxpayer.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface TaxpayerFiscalLookupUseCase {

    Optional<TaxpayerFiscalData> findFiscalDataById(
            UUID taxpayerId
    );
}