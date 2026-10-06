package io.github.imecuadorian.emitta.establishment.adapter.in.web;

import io.github.imecuadorian.emitta.establishment.domain.Establishment;

import java.time.Instant;
import java.util.UUID;

public record EstablishmentResponse(
        UUID id,
        UUID taxpayerId,
        String code,
        String name,
        String address,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static EstablishmentResponse from(
            Establishment establishment
    ) {
        return new EstablishmentResponse(
                establishment.getId(),
                establishment.getTaxpayerId(),
                establishment.getCode().value(),
                establishment.getName(),
                establishment.getAddress(),
                establishment.getStatus().name(),
                establishment.getCreatedAt(),
                establishment.getUpdatedAt()
        );
    }
}