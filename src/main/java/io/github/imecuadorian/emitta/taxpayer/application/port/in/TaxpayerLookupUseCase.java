package io.github.imecuadorian.emitta.taxpayer.application.port.in;

import java.util.UUID;

public interface TaxpayerLookupUseCase {

    boolean existsById(UUID taxpayerId);
}