package io.github.imecuadorian.emitta.taxpayer.application.service;

import io.github.imecuadorian.emitta.taxpayer.application.command.CreateTaxpayerCommand;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TaxpayerAlreadyExistsException;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TenantNotFoundException;
import io.github.imecuadorian.emitta.taxpayer.application.port.out.TaxpayerRepository;
import io.github.imecuadorian.emitta.taxpayer.domain.Ruc;
import io.github.imecuadorian.emitta.taxpayer.domain.Taxpayer;
import io.github.imecuadorian.emitta.taxpayer.domain.TaxpayerStatus;
import io.github.imecuadorian.emitta.tenant.application.port.in.TenantLookupUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTaxpayerServiceTest {

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "4d34da33-c6e4-4379-8ab0-19fef817cc67"
            );

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "5068da12-fc37-4e12-a50e-05adc629d7c7"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-05T18:00:00Z"
            );

    @Mock
    private TaxpayerRepository taxpayerRepository;

    @Mock
    private TenantLookupUseCase tenantLookup;

    private CreateTaxpayerService service;

    @BeforeEach
    void setUp() {

        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        service =
                new CreateTaxpayerService(
                        taxpayerRepository,
                        tenantLookup,
                        clock,
                        () -> TAXPAYER_ID
                );
    }

    @Test
    void shouldCreateTaxpayer() {

        when(
                tenantLookup.existsById(TENANT_ID)
        ).thenReturn(true);

        when(
                taxpayerRepository
                        .existsByTenantIdAndRuc(
                                TENANT_ID,
                                new Ruc("1790012345001")
                        )
        ).thenReturn(false);

        when(
                taxpayerRepository.save(
                        any(Taxpayer.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        Taxpayer taxpayer =
                service.create(
                        validCommand()
                );

        assertEquals(
                TAXPAYER_ID,
                taxpayer.getId()
        );

        assertEquals(
                TENANT_ID,
                taxpayer.getTenantId()
        );

        assertEquals(
                "1790012345001",
                taxpayer.getRuc().value()
        );

        assertEquals(
                "NEXORF S.A.S.",
                taxpayer.getLegalName()
        );

        assertEquals(
                TaxpayerStatus.ACTIVE,
                taxpayer.getStatus()
        );

        assertTrue(
                taxpayer.isTestEnabled()
        );

        assertFalse(
                taxpayer.isProductionEnabled()
        );

        assertEquals(
                NOW,
                taxpayer.getCreatedAt()
        );

        verify(
                taxpayerRepository
        ).save(
                any(Taxpayer.class)
        );
    }

    @Test
    void shouldRejectUnknownTenant() {

        when(
                tenantLookup.existsById(TENANT_ID)
        ).thenReturn(false);

        assertThrows(
                TenantNotFoundException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                taxpayerRepository,
                never()
        ).save(
                any(Taxpayer.class)
        );
    }

    @Test
    void shouldRejectDuplicatedRucWithinTenant() {

        when(
                tenantLookup.existsById(TENANT_ID)
        ).thenReturn(true);

        when(
                taxpayerRepository
                        .existsByTenantIdAndRuc(
                                TENANT_ID,
                                new Ruc(
                                        "1790012345001"
                                )
                        )
        ).thenReturn(true);

        assertThrows(
                TaxpayerAlreadyExistsException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                taxpayerRepository,
                never()
        ).save(
                any(Taxpayer.class)
        );
    }

    private CreateTaxpayerCommand validCommand() {

        return new CreateTaxpayerCommand(
                TENANT_ID,
                "1790012345001",
                "NEXORF S.A.S.",
                "NEXORF",
                "Quito"
        );
    }
}