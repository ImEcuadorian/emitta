package io.github.imecuadorian.emitta.pointofissue.application.port.in;

import java.util.UUID;

public record PointOfIssueFiscalData(
        UUID id,
        UUID establishmentId,
        String code,
        boolean active
) {
}