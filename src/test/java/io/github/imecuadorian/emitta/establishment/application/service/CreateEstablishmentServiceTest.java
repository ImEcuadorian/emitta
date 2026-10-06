package io.github.imecuadorian.emitta.establishment.application.service;

import io.github.imecuadorian.emitta.establishment.application.command.CreateEstablishmentCommand;
import io.github.imecuadorian.emitta.establishment.application.exception.EstablishmentAlreadyExistsException;
import io.github.imecuadorian.emitta.establishment.application.exception.TaxpayerNotFoundException;
import io.github.imecuadorian.emitta.establishment.application.port.out.EstablishmentRepository;
import io.github.imecuadorian.emitta.establishment.domain.Establishment;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentCode;
import io.github.imecuadorian.emitta.establishment.domain.EstablishmentStatus;
import io.github.imecuadorian.emitta.taxpayer.application.port.in.TaxpayerLookupUseCase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEstablishmentServiceTest {

    private static final UUID TAXPAYER_ID =
            UUID.fromString(
                    "5068da12-fc37-4e12-a50e-05adc629d7c7"
            );

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "d41de43c-eb87-445f-b02c-baeae6593375"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-05T23:30:00Z"
            );

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private TaxpayerLookupUseCase taxpayerLookup;

    private CreateEstablishmentService service;

    @BeforeEach
    void setUp() {

        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        service =
                new CreateEstablishmentService(
                        establishmentRepository,
                        taxpayerLookup,
                        clock,
                        () -> ESTABLISHMENT_ID
                );
    }

    @Test
    void shouldCreateEstablishment() {

        when(
                taxpayerLookup.existsById(
                        TAXPAYER_ID
                )
        ).thenReturn(true);

        when(
                establishmentRepository
                        .existsByTaxpayerIdAndCode(
                                TAXPAYER_ID,
                                new EstablishmentCode("001")
                        )
        ).thenReturn(false);

        when(
                establishmentRepository.save(
                        any(Establishment.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        Establishment establishment =
                service.create(
                        validCommand()
                );

        assertEquals(
                ESTABLISHMENT_ID,
                establishment.getId()
        );

        assertEquals(
                TAXPAYER_ID,
                establishment.getTaxpayerId()
        );

        assertEquals(
                "001",
                establishment.getCode().value()
        );

        assertEquals(
                "Matriz Quito",
                establishment.getName()
        );

        assertEquals(
                EstablishmentStatus.ACTIVE,
                establishment.getStatus()
        );

        assertEquals(
                NOW,
                establishment.getCreatedAt()
        );

        verify(
                establishmentRepository
        ).save(
                any(Establishment.class)
        );
    }

    @Test
    void shouldRejectUnknownTaxpayer() {

        when(
                taxpayerLookup.existsById(
                        TAXPAYER_ID
                )
        ).thenReturn(false);

        assertThrows(
                TaxpayerNotFoundException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                establishmentRepository,
                never()
        ).save(
                any(Establishment.class)
        );
    }

    @Test
    void shouldRejectDuplicatedCode() {

        when(
                taxpayerLookup.existsById(
                        TAXPAYER_ID
                )
        ).thenReturn(true);

        when(
                establishmentRepository
                        .existsByTaxpayerIdAndCode(
                                TAXPAYER_ID,
                                new EstablishmentCode("001")
                        )
        ).thenReturn(true);

        assertThrows(
                EstablishmentAlreadyExistsException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                establishmentRepository,
                never()
        ).save(
                any(Establishment.class)
        );
    }

    private CreateEstablishmentCommand validCommand() {

        return new CreateEstablishmentCommand(
                TAXPAYER_ID,
                "001",
                "Matriz Quito",
                "Av. Principal 123"
        );
    }
}