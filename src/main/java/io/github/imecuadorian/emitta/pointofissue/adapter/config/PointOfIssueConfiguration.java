package io.github.imecuadorian.emitta.pointofissue.adapter.config;

import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.CreatePointOfIssueUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.out.PointOfIssueRepository;
import io.github.imecuadorian.emitta.pointofissue.application.service.CreatePointOfIssueService;
import io.github.imecuadorian.emitta.pointofissue.application.service.PointOfIssueFiscalLookupService;
import io.github.imecuadorian.emitta.pointofissue.application.service.PointOfIssueLookupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class PointOfIssueConfiguration {

    @Bean
    CreatePointOfIssueUseCase createPointOfIssueUseCase(
            PointOfIssueRepository pointOfIssueRepository,
            EstablishmentLookupUseCase establishmentLookupUseCase,
            Clock clock
    ) {
        return new CreatePointOfIssueService(
                pointOfIssueRepository,
                establishmentLookupUseCase,
                clock,
                UUID::randomUUID
        );
    }

    @Bean
    PointOfIssueLookupUseCase pointOfIssueLookupUseCase(
            PointOfIssueRepository pointOfIssueRepository
    ) {
        return new PointOfIssueLookupService(
                pointOfIssueRepository
        );
    }

    @Bean
    PointOfIssueFiscalLookupUseCase pointOfIssueFiscalLookupUseCase(
            PointOfIssueRepository pointOfIssueRepository
    ) {
        return new PointOfIssueFiscalLookupService(
                pointOfIssueRepository
        );
    }
}