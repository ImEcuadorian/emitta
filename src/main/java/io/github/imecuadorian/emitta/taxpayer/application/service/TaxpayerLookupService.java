package io.github.imecuadorian.emitta.taxpayer.application.service;

import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerLookupUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.port.out.TaxpayerRepository;

import java.util.Objects;
import java.util.UUID;

public final class TaxpayerLookupService
        implements TaxpayerLookupUseCase {

    private final TaxpayerRepository taxpayerRepository;

    public TaxpayerLookupService(
            TaxpayerRepository taxpayerRepository
    ) {
        this.taxpayerRepository =
                Objects.requireNonNull(taxpayerRepository);
    }

    @Override
    public boolean existsById(UUID taxpayerId) {

        Objects.requireNonNull(
                taxpayerId,
                "Taxpayer id cannot be null"
        );

        return taxpayerRepository.existsById(
                taxpayerId
        );
    }
}