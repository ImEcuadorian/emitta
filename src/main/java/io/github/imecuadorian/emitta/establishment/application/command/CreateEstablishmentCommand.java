package io.github.imecuadorian.emitta.establishment.application.command;

import java.util.UUID;

public record CreateEstablishmentCommand(
        UUID taxpayerId,
        String code,
        String name,
        String address
) {
}