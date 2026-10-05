package io.github.imecuadorian.emitta.taxpayer.adapter.config;

import io.github.imecuadorian.emitta.taxpayer.application.port.in.CreateTaxpayerUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.port.out.TaxpayerRepository;
import io.github.imecuadorian.emitta.taxpayer.application.service.CreateTaxpayerService;
import io.github.imecuadorian.emitta.tenant.application.port.in.TenantLookupUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class TaxpayerConfiguration {

    @Bean
    CreateTaxpayerUseCase createTaxpayerUseCase(
            TaxpayerRepository taxpayerRepository,
            TenantLookupUseCase tenantLookupUseCase,
            Clock clock
    ) {
        return new CreateTaxpayerService(
                taxpayerRepository,
                tenantLookupUseCase,
                clock,
                UUID::randomUUID
        );
    }
}