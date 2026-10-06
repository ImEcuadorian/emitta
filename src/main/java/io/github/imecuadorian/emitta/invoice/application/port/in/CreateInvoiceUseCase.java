package io.github.imecuadorian.emitta.invoice.application.port.in;

import io.github.imecuadorian.emitta.invoice.application.command.CreateInvoiceCommand;
import io.github.imecuadorian.emitta.invoice.application.model.CreateInvoiceResult;

public interface CreateInvoiceUseCase {

    CreateInvoiceResult create(
            CreateInvoiceCommand command
    );
}