package io.github.imecuadorian.emitta.establishment.adapter.config;

import io.github.imecuadorian.emitta.establishment.application.port.in.CreateEstablishmentUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentLookupUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.out.EstablishmentRepository;
import io.github.imecuadorian.emitta.establishment.application.service.CreateEstablishmentService;
import io.github.imecuadorian.emitta.establishment.application.service.EstablishmentFiscalLookupService;
import io.github.imecuadorian.emitta.establishment.application.service.EstablishmentLookupService;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerLookupUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class EstablishmentConfiguration {

    @Bean
    CreateEstablishmentUseCase createEstablishmentUseCase(
            EstablishmentRepository establishmentRepository,
            TaxpayerLookupUseCase taxpayerLookupUseCase,
            Clock clock
    ) {
        return new CreateEstablishmentService(
                establishmentRepository,
                taxpayerLookupUseCase,
                clock,
                UUID::randomUUID
        );
    }

    @Bean
    EstablishmentLookupUseCase establishmentLookupUseCase(
            EstablishmentRepository establishmentRepository
    ) {
        return new EstablishmentLookupService(
                establishmentRepository
        );
    }

    @Bean
    EstablishmentFiscalLookupUseCase establishmentFiscalLookupUseCase(
            EstablishmentRepository establishmentRepository
    ) {
        return new EstablishmentFiscalLookupService(
                establishmentRepository
        );
    }
}