package io.github.imecuadorian.emitta.taxpayer.application.port.in;

import io.github.imecuadorian.emitta.taxpayer.application.command.CreateTaxpayerCommand;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;

public interface CreateTaxpayerUseCase {

    Taxpayer create(
            CreateTaxpayerCommand command
    );
}