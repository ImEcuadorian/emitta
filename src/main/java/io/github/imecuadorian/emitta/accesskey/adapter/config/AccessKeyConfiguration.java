package io.github.imecuadorian.emitta.accesskey.adapter.config;

import io.github.imecuadorian.emitta.accesskey.application.port.in.GenerateAccessKeyUseCase;
import io.github.imecuadorian.emitta.accesskey.application.port.out.NumericCodeGenerator;
import io.github.imecuadorian.emitta.accesskey.application.service.GenerateAccessKeyService;
import io.github.imecuadorian.emitta.documentsequence.application.port.in.AllocateSequentialUseCase;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessKeyConfiguration {

    @Bean
    GenerateAccessKeyUseCase generateAccessKeyUseCase(
            PointOfIssueFiscalLookupUseCase pointOfIssueLookup,
            EstablishmentFiscalLookupUseCase establishmentLookup,
            TaxpayerFiscalLookupUseCase taxpayerLookup,
            AllocateSequentialUseCase allocateSequentialUseCase,
            NumericCodeGenerator numericCodeGenerator
    ) {
        return new GenerateAccessKeyService(
                pointOfIssueLookup,
                establishmentLookup,
                taxpayerLookup,
                allocateSequentialUseCase,
                numericCodeGenerator
        );
    }
}