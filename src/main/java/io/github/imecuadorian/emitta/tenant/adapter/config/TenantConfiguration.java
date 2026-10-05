package io.github.imecuadorian.emitta.tenant.adapter.config;

import io.github.imecuadorian.emitta.tenant.application.port.in.CreateTenantUseCase;
import io.github.imecuadorian.emitta.tenant.application.port.in.TenantLookupUseCase;
import io.github.imecuadorian.emitta.tenant.application.port.out.TenantRepository;
import io.github.imecuadorian.emitta.tenant.application.service.CreateTenantService;
import io.github.imecuadorian.emitta.tenant.application.service.TenantLookupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class TenantConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CreateTenantUseCase createTenantUseCase(
            TenantRepository tenantRepository,
            Clock clock
    ) {
        return new CreateTenantService(
                tenantRepository,
                clock,
                UUID::randomUUID
        );
    }

    @Bean
    TenantLookupUseCase tenantLookupUseCase(
            TenantRepository tenantRepository
    ) {
        return new TenantLookupService(
                tenantRepository
        );
    }
}