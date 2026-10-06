package io.github.imecuadorian.emitta.pointofissue.application.service;

import io.github.imecuadorian.emitta.establishment.application.port.in.EstablishmentLookupUseCase;
import io.github.imecuadorian.emitta.pointofissue.application.command.CreatePointOfIssueCommand;
import io.github.imecuadorian.emitta.pointofissue.application.exception.EstablishmentNotFoundException;
import io.github.imecuadorian.emitta.pointofissue.application.exception.PointOfIssueAlreadyExistsException;
import io.github.imecuadorian.emitta.pointofissue.application.port.out.PointOfIssueRepository;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssue;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueCode;
import io.github.imecuadorian.emitta.pointofissue.domain.PointOfIssueStatus;
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
class CreatePointOfIssueServiceTest {

    private static final UUID ESTABLISHMENT_ID =
            UUID.fromString(
                    "d41de43c-eb87-445f-b02c-baeae6593375"
            );

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44ee5a49-334a-43c1-a57a-b1bb47ee60a4"
            );

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-06T00:30:00Z"
            );

    @Mock
    private PointOfIssueRepository pointOfIssueRepository;

    @Mock
    private EstablishmentLookupUseCase establishmentLookup;

    private CreatePointOfIssueService service;

    @BeforeEach
    void setUp() {

        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        service =
                new CreatePointOfIssueService(
                        pointOfIssueRepository,
                        establishmentLookup,
                        clock,
                        () -> POINT_OF_ISSUE_ID
                );
    }

    @Test
    void shouldCreatePointOfIssue() {

        when(
                establishmentLookup.existsById(
                        ESTABLISHMENT_ID
                )
        ).thenReturn(true);

        when(
                pointOfIssueRepository
                        .existsByEstablishmentIdAndCode(
                                ESTABLISHMENT_ID,
                                new PointOfIssueCode("001")
                        )
        ).thenReturn(false);

        when(
                pointOfIssueRepository.save(
                        any(PointOfIssue.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        PointOfIssue pointOfIssue =
                service.create(
                        validCommand()
                );

        assertEquals(
                POINT_OF_ISSUE_ID,
                pointOfIssue.getId()
        );

        assertEquals(
                ESTABLISHMENT_ID,
                pointOfIssue.getEstablishmentId()
        );

        assertEquals(
                "001",
                pointOfIssue.getCode().value()
        );

        assertEquals(
                "Caja Principal",
                pointOfIssue.getName()
        );

        assertEquals(
                PointOfIssueStatus.ACTIVE,
                pointOfIssue.getStatus()
        );

        assertEquals(
                NOW,
                pointOfIssue.getCreatedAt()
        );

        verify(
                pointOfIssueRepository
        ).save(
                any(PointOfIssue.class)
        );
    }

    @Test
    void shouldRejectUnknownEstablishment() {

        when(
                establishmentLookup.existsById(
                        ESTABLISHMENT_ID
                )
        ).thenReturn(false);

        assertThrows(
                EstablishmentNotFoundException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                pointOfIssueRepository,
                never()
        ).save(
                any(PointOfIssue.class)
        );
    }

    @Test
    void shouldRejectDuplicatedCode() {

        when(
                establishmentLookup.existsById(
                        ESTABLISHMENT_ID
                )
        ).thenReturn(true);

        when(
                pointOfIssueRepository
                        .existsByEstablishmentIdAndCode(
                                ESTABLISHMENT_ID,
                                new PointOfIssueCode("001")
                        )
        ).thenReturn(true);

        assertThrows(
                PointOfIssueAlreadyExistsException.class,
                () -> service.create(
                        validCommand()
                )
        );

        verify(
                pointOfIssueRepository,
                never()
        ).save(
                any(PointOfIssue.class)
        );
    }

    private CreatePointOfIssueCommand validCommand() {

        return new CreatePointOfIssueCommand(
                ESTABLISHMENT_ID,
                "001",
                "Caja Principal"
        );
    }
}