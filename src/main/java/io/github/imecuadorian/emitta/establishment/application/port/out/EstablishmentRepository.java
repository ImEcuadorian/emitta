package io.github.imecuadorian.emitta.establishment.application.port.out;

import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;

import java.util.Optional;
import java.util.UUID;

public interface EstablishmentRepository {

    Establishment save(Establishment establishment);

    Optional<Establishment> findById(UUID id);

    boolean existsById(UUID id);

    boolean existsByTaxpayerIdAndCode(
            UUID taxpayerId,
            EstablishmentCode code
    );
}