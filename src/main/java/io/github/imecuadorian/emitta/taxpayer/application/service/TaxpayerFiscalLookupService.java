package io.github.imecuadorian.emitta.taxpayer.application.service;

import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalData;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.port.out.TaxpayerRepository;
import io.github.imecuadorian.emitta.taxpayer.domain.TaxpayerStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TaxpayerFiscalLookupService
        implements TaxpayerFiscalLookupUseCase {

    private final TaxpayerRepository repository;

    public TaxpayerFiscalLookupService(
            TaxpayerRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(repository);
    }

    @Override
    public Optional<TaxpayerFiscalData> findFiscalDataById(
            UUID taxpayerId
    ) {

        Objects.requireNonNull(
                taxpayerId,
                "Taxpayer id cannot be null"
        );

        return repository
                .findById(taxpayerId)
                .map(taxpayer ->
                        new TaxpayerFiscalData(
                                taxpayer.getId(),
                                taxpayer.getRuc().value(),
                                taxpayer.getStatus()
                                        == TaxpayerStatus.ACTIVE,
                                taxpayer.isTestEnabled(),
                                taxpayer.isProductionEnabled()
                        )
                );
    }
}