package io.github.imecuadorian.emitta.taxpayer.application.port.out;

import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;

import java.util.Optional;
import java.util.UUID;

public interface TaxpayerRepository {

    Taxpayer save(Taxpayer taxpayer);

    Optional<Taxpayer> findById(UUID id);

    boolean existsById(UUID id);

    boolean existsByTenantIdAndRuc(
            UUID tenantId,
            Ruc ruc
    );
}