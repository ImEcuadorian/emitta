package io.github.imecuadorian.emitta.establishment.application.port.in;

import java.util.UUID;

public record EstablishmentFiscalData(
        UUID id,
        UUID taxpayerId,
        String code,
        boolean active
) {
}