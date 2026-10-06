package io.github.imecuadorian.emitta.establishment.application.port.in;

import java.util.UUID;

public interface EstablishmentLookupUseCase {

    boolean existsById(UUID establishmentId);
}