package io.github.imecuadorian.emitta.tenant.application.service;

import io.github.imecuadorian.emitta.tenant.application.command.CreateTenantCommand;
import io.github.imecuadorian.emitta.tenant.application.port.in.CreateTenantUseCase;
import io.github.imecuadorian.emitta.tenant.application.port.out.TenantRepository;
import io.github.imecuadorian.emitta.tenant.domain.Tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CreateTenantService
        implements CreateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public CreateTenantService(
            TenantRepository tenantRepository,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.tenantRepository =
                Objects.requireNonNull(tenantRepository);

        this.clock =
                Objects.requireNonNull(clock);

        this.idGenerator =
                Objects.requireNonNull(idGenerator);
    }

    @Override
    public Tenant create(CreateTenantCommand command) {

        Objects.requireNonNull(
                command,
                "Create tenant command cannot be null"
        );

        UUID tenantId = idGenerator.get();

        Instant now = clock.instant();

        Tenant tenant = Tenant.create(
                tenantId,
                command.name(),
                now
        );

        return tenantRepository.save(tenant);
    }
}