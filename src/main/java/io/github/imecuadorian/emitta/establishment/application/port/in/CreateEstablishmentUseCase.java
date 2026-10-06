package io.github.imecuadorian.emitta.establishment.application.port.in;

import io.github.imecuadorian.emitta.establishment.application.command.CreateEstablishmentCommand;
import io.github.imecuadorian.emitta.establishment.domain.Establishment;

public interface CreateEstablishmentUseCase {

    Establishment create(
            CreateEstablishmentCommand command
    );
}