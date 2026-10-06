package io.github.imecuadorian.emitta.documentsequence.application.service;

import io.github.imecuadorian.emitta.documentsequence.application.command.AllocateSequentialCommand;
import io.github.imecuadorian.emitta.documentsequence.application.exception.PointOfIssueInactiveException;
import io.github.imecuadorian.emitta.documentsequence.application.exception.PointOfIssueNotFoundException;
import io.github.imecuadorian.emitta.documentsequence.application.port.out.SequenceAllocationPort;
import io.github.imecuadorian.emitta.documentsequence.domain.SequenceScope;
import io.github.imecuadorian.emitta.documentsequence.domain.SequentialNumber;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupResult;
import io.github.imecuadorian.emitta.pointofissue.application.port.in.PointOfIssueLookupUseCase;
import io.github.imecuadorian.emitta.shared.fiscal.DocumentType;
import io.github.imecuadorian.emitta.shared.fiscal.FiscalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocateSequentialServiceTest {

    private static final UUID POINT_OF_ISSUE_ID =
            UUID.fromString(
                    "44ee5a49-334a-43c1-a57a-b1bb47ee60a4"
            );

    @Mock
    private SequenceAllocationPort sequenceAllocationPort;

    @Mock
    private PointOfIssueLookupUseCase pointOfIssueLookup;

    private AllocateSequentialService service;

    @BeforeEach
    void setUp() {
        service =
                new AllocateSequentialService(
                        sequenceAllocationPort,
                        pointOfIssueLookup
                );
    }

    @Test
    void shouldAllocateSequentialForActivePointOfIssue() {

        when(
                pointOfIssueLookup.findById(
                        POINT_OF_ISSUE_ID
                )
        ).thenReturn(
                Optional.of(
                        new PointOfIssueLookupResult(
                                POINT_OF_ISSUE_ID,
                                true
                        )
                )
        );

        when(
                sequenceAllocationPort.allocateNext(
                        new SequenceScope(
                                POINT_OF_ISSUE_ID,
                                DocumentType.INVOICE,
                                FiscalEnvironment.PRODUCTION
                        )
                )
        ).thenReturn(
                new SequentialNumber(42)
        );

        SequentialNumber result =
                service.allocate(
                        validCommand()
                );

        assertEquals(
                42L,
                result.value()
        );

        assertEquals(
                "000000042",
                result.formatted()
        );

        ArgumentCaptor<SequenceScope> captor =
                ArgumentCaptor.forClass(
                        SequenceScope.class
                );

        verify(
                sequenceAllocationPort
        ).allocateNext(
                captor.capture()
        );

        SequenceScope scope =
                captor.getValue();

        assertEquals(
                POINT_OF_ISSUE_ID,
                scope.pointOfIssueId()
        );

        assertEquals(
                DocumentType.INVOICE,
                scope.documentType()
        );

        assertEquals(
                FiscalEnvironment.PRODUCTION,
                scope.environment()
        );
    }

    @Test
    void shouldRejectUnknownPointOfIssue() {

        when(
                pointOfIssueLookup.findById(
                        POINT_OF_ISSUE_ID
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                PointOfIssueNotFoundException.class,
                () -> service.allocate(
                        validCommand()
                )
        );

        verify(
                sequenceAllocationPort,
                never()
        ).allocateNext(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldRejectInactivePointOfIssue() {

        when(
                pointOfIssueLookup.findById(
                        POINT_OF_ISSUE_ID
                )
        ).thenReturn(
                Optional.of(
                        new PointOfIssueLookupResult(
                                POINT_OF_ISSUE_ID,
                                false
                        )
                )
        );

        assertThrows(
                PointOfIssueInactiveException.class,
                () -> service.allocate(
                        validCommand()
                )
        );

        verify(
                sequenceAllocationPort,
                never()
        ).allocateNext(
                org.mockito.ArgumentMatchers.any()
        );
    }

    private AllocateSequentialCommand validCommand() {

        return new AllocateSequentialCommand(
                POINT_OF_ISSUE_ID,
                DocumentType.INVOICE,
                FiscalEnvironment.PRODUCTION
        );
    }
}