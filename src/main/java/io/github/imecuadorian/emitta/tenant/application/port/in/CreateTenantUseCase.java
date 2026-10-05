package io.github.imecuadorian.emitta.tenant.application.port.in;

import io.github.imecuadorian.emitta.tenant.application.command.CreateTenantCommand;
import io.github.imecuadorian.emitta.tenant.domain.Tenant;

public interface CreateTenantUseCase {

    Tenant create(CreateTenantCommand command);

}