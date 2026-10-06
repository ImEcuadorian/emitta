package io.github.imecuadorian.emitta.pointofissue.application.exception;

import java.util.UUID;

public final class EstablishmentNotFoundException
        extends RuntimeException {

    private final UUID establishmentId;

    public EstablishmentNotFoundException(
            UUID establishmentId
    ) {
        super(
                "Establishment not found: "
                        + establishmentId
        );

        this.establishmentId = establishmentId;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }
}