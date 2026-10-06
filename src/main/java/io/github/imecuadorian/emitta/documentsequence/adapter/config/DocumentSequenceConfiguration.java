package io.github.imecuadorian.emitta.documentsequence.adapter.config;

import io.github.imecuadorian.emitta.documentsequence.application.port.in.AllocateSequentialUseCase;
import io.github.imecuadorian.emitta.documentsequence.application.port.out.SequenceAllocationPort;
import io.github.imecuadorian.emitta.documentsequence.application.service.AllocateSequentialService;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentSequenceConfiguration {

    @Bean
    AllocateSequentialUseCase allocateSequentialUseCase(
            SequenceAllocationPort sequenceAllocationPort,
            PointOfIssueLookupUseCase pointOfIssueLookupUseCase
    ) {
        return new AllocateSequentialService(
                sequenceAllocationPort,
                pointOfIssueLookupUseCase
        );
    }
}