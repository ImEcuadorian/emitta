package io.github.imecuadorian.emitta.accesskey.application.service;

import io.github.imecuadorian.emitta.accesskey.application.command.GenerateAccessKeyCommand;
import io.github.imecuadorian.emitta.accesskey.application.exception.FiscalEnvironmentDisabledException;
import io.github.imecuadorian.emitta.accesskey.application.model.GeneratedAccessKey;
import io.github.imecuadorian.emitta.accesskey.application.port.out.NumericCodeGenerator;
import io.github.imecuadorian.emitta.documentsequence.application.port.in.AllocateSequentialUseCase;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalData;
import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentFiscalLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalData;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueFiscalLookupUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalData;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerFiscalLookupUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateAccessKeyServiceTest {

    private static final UUID TAXPAYER_ID =
            UUID.randomUUID();

    private static final UUID ESTABLISHMENT_ID =
            UUID.randomUUID();

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.randomUUID();

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "4d34da33-c6e4-4379-8ab0-19fef817cc67"
            );

    @Mock
    private PointOfIssueFiscalLookupUseCase
            pointOfIssueLookup;

    @Mock
    private EstablishmentFiscalLookupUseCase
            establishmentLookup;

    @Mock
    private TaxpayerFiscalLookupUseCase
            taxpayerLookup;

    @Mock
    private AllocateSequentialUseCase
            allocateSequentialUseCase;

    @Mock
    private NumericCodeGenerator
            numericCodeGenerator;

    private GenerateAccessKeyService service;

    @BeforeEach
    void setUp() {

        service =
                new GenerateAccessKeyService(
                        pointOfIssueLookup,
                        establishmentLookup,
                        taxpayerLookup,
                        allocateSequentialUseCase,
                        numericCodeGenerator
                );
    }

    @Test
    void shouldGenerateOfficialSriAccessKeyExample() {

        configureActiveFiscalHierarchy();

        when(
                allocateSequentialUseCase.allocate(
                        any()
                )
        ).thenReturn(
                new SequentialNumber(124)
        );

        when(
                numericCodeGenerator.generate()
        ).thenReturn(
                "12345678"
        );

        GeneratedAccessKey generated =
                service.generate(
                        new GenerateAccessKeyCommand(
                                POINT_OF_ISSUE_ID,
                                DocumentType.DEBIT_NOTE,
                                FiscalEnvironment.TEST,
                                LocalDate.of(
                                        2024,
                                        11,
                                        21
                                )
                        )
                );

        assertEquals(
                "2111202405176001321000110010010000001241234567810",
                generated.accessKey().value()
        );

        assertEquals(
                "000000124",
                generated.sequential().formatted()
        );

        assertEquals(
                "12345678",
                generated.numericCode()
        );
    }

    @Test
    void shouldNotAllocateSequentialWhenEnvironmentIsDisabled() {

        UUID tenantId =
                UUID.randomUUID();

        when(
                pointOfIssueLookup.findFiscalDataById(
                        POINT_OF_ISSUE_ID
                )
        ).thenReturn(
                Optional.of(
                        new PointOfIssueFiscalData(
                                POINT_OF_ISSUE_ID,
                                ESTABLISHMENT_ID,
                                "001",
                                true
                        )
                )
        );

        when(
                establishmentLookup.findFiscalDataById(
                        ESTABLISHMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        new EstablishmentFiscalData(
                                ESTABLISHMENT_ID,
                                TAXPAYER_ID,
                                "001",
                                true
                        )
                )
        );

        when(
                taxpayerLookup.findFiscalDataById(
                        TAXPAYER_ID
                )
        ).thenReturn(
                Optional.of(
                        new TaxpayerFiscalData(
                                TAXPAYER_ID,
                                tenantId,
                                "1760013210001",
                                true,
                                true,
                                false
                        )
                )
        );

        assertThrows(
                FiscalEnvironmentDisabledException.class,
                () -> service.generate(
                        new GenerateAccessKeyCommand(
                                POINT_OF_ISSUE_ID,
                                DocumentType.INVOICE,
                                FiscalEnvironment.PRODUCTION,
                                LocalDate.of(
                                        2026,
                                        10,
                                        5
                                )
                        )
                )
        );

        verify(
                allocateSequentialUseCase,
                never()
        ).allocate(
                any()
        );

        verify(
                numericCodeGenerator,
                never()
        ).generate();
    }

    private void configureActiveFiscalHierarchy() {

        when(
                pointOfIssueLookup.findFiscalDataById(
                        POINT_OF_ISSUE_ID
                )
        ).thenReturn(
                Optional.of(
                        new PointOfIssueFiscalData(
                                POINT_OF_ISSUE_ID,
                                ESTABLISHMENT_ID,
                                "001",
                                true
                        )
                )
        );

        when(
                establishmentLookup.findFiscalDataById(
                        ESTABLISHMENT_ID
                )
        ).thenReturn(
                Optional.of(
                        new EstablishmentFiscalData(
                                ESTABLISHMENT_ID,
                                TAXPAYER_ID,
                                "001",
                                true
                        )
                )
        );

        when(
                taxpayerLookup.findFiscalDataById(
                        TAXPAYER_ID
                )
        ).thenReturn(
                Optional.of(
                        new TaxpayerFiscalData(
                                TAXPAYER_ID,
                                TENANT_ID,
                                "1760013210001",
                                true,
                                true,
                                true
                        )
                )
        );
    }
}