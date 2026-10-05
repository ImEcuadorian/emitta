package io.github.imecuadorian.emitta.taxpayer.application.service;

import io.github.imecuadorian.emitta.taxpayer.application.command.CreateTaxpayerCommand;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TaxpayerAlreadyExistsException;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TenantNotFoundException;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.CreateTaxpayerUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.port.out.TaxpayerRepository;
import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;
import io.github.imecuadorian.emitta.tenant.application.port.in.TenantLookupUseCase;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CreateTaxpayerService
        implements CreateTaxpayerUseCase {

    private final TaxpayerRepository taxpayerRepository;
    private final TenantLookupUseCase tenantLookup;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public CreateTaxpayerService(
            TaxpayerRepository taxpayerRepository,
            TenantLookupUseCase tenantLookup,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.taxpayerRepository =
                Objects.requireNonNull(taxpayerRepository);

        this.tenantLookup =
                Objects.requireNonNull(tenantLookup);

        this.clock =
                Objects.requireNonNull(clock);

        this.idGenerator =
                Objects.requireNonNull(idGenerator);
    }

    @Override
    public Taxpayer create(
            CreateTaxpayerCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Create taxpayer command cannot be null"
        );

        UUID tenantId = Objects.requireNonNull(
                command.tenantId(),
                "Tenant id cannot be null"
        );

        if (!tenantLookup.existsById(tenantId)) {
            throw new TenantNotFoundException(
                    tenantId
            );
        }

        Ruc ruc =
                new Ruc(command.ruc());

        if (
                taxpayerRepository
                        .existsByTenantIdAndRuc(
                                tenantId,
                                ruc
                        )
        ) {
            throw new TaxpayerAlreadyExistsException(
                    ruc.value()
            );
        }

        Instant now =
                clock.instant();

        Taxpayer taxpayer =
                Taxpayer.create(
                        idGenerator.get(),
                        tenantId,
                        ruc,
                        command.legalName(),
                        command.tradeName(),
                        command.mainAddress(),
                        now
                );

        return taxpayerRepository.save(
                taxpayer
        );
    }
}