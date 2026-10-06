package io.github.imecuadorian.emitta.pointofissue.application.port.in;

import java.util.UUID;

public record PointOfIssueLookupResult(
        UUID id,
        boolean active
) {
}