package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttempt;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttemptResult;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentRejectedUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentSubmittedUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.ScheduleDocumentRetryUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitSignedDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionAttemptPort;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitFiscalDocumentToSriServiceTest {

    private static final UUID DOCUMENT_ID =
            UUID.fromString(
                    "11111111-2222-3333-4444-555555555555"
            );

    @Mock
    private MarkDocumentSubmittedUseCase
            markDocumentSubmittedUseCase;

    @Mock
    private SubmitSignedDocumentToSriUseCase
            submitSignedDocumentToSriUseCase;

    @Mock
    private MarkDocumentRejectedUseCase
            markDocumentRejectedUseCase;

    @Mock
    private ScheduleDocumentRetryUseCase
            scheduleDocumentRetryUseCase;

    private static final Instant NOW =
            Instant.parse("2026-10-07T22:00:00Z");

    @Mock
    private SriReceptionAttemptPort sriReceptionAttemptPort;

    private SubmitFiscalDocumentToSriService service;

    @BeforeEach
    void setUp() {

        service =
                new SubmitFiscalDocumentToSriService(
                        markDocumentSubmittedUseCase,
                        submitSignedDocumentToSriUseCase,
                        markDocumentRejectedUseCase,
                        scheduleDocumentRetryUseCase,
                        sriReceptionAttemptPort,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }
    @Test
    void shouldMarkSubmittedBeforeCallingSri() {

        stubStartedAttempt();

        SriReceptionResult sriResult =
                new SriReceptionResult(
                        SriReceptionStatus.RECEIVED,
                        List.of()
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenReturn(
                sriResult
        );

        SriReceptionResult result =
                service.submit(
                        DOCUMENT_ID
                );

        assertSame(
                sriResult,
                result
        );

        InOrder order =
                inOrder(
                        markDocumentSubmittedUseCase,
                        submitSignedDocumentToSriUseCase
                );

        order.verify(
                markDocumentSubmittedUseCase
        ).markSubmitted(
                DOCUMENT_ID
        );

        order.verify(
                submitSignedDocumentToSriUseCase
        ).submit(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldNotCallSriWhenMarkSubmittedFails() {

        RuntimeException failure =
                new RuntimeException(
                        "Database unavailable"
                );

        when(
                markDocumentSubmittedUseCase.markSubmitted(
                        DOCUMENT_ID
                )
        ).thenThrow(
                failure
        );

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.submit(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                submitSignedDocumentToSriUseCase,
                never()
        ).submit(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldPropagateSriFailureAfterSubmissionWasStarted() {

        stubStartedAttempt();

        RuntimeException failure =
                new RuntimeException(
                        "SRI unavailable"
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenThrow(
                failure
        );

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.submit(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                markDocumentSubmittedUseCase
        ).markSubmitted(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldKeepSubmittedWhenSriReceivesDocument() {

        stubStartedAttempt();

        SriReceptionResult result =
                new SriReceptionResult(
                        SriReceptionStatus.RECEIVED,
                        List.of()
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenReturn(
                result
        );

        SriReceptionResult returned =
                service.submit(
                        DOCUMENT_ID
                );

        assertSame(
                result,
                returned
        );

        verify(
                markDocumentRejectedUseCase,
                never()
        ).markRejected(
                DOCUMENT_ID
        );

        verify(
                scheduleDocumentRetryUseCase,
                never()
        ).scheduleRetry(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldRejectDocumentWhenSriReturnsIt() {

        stubStartedAttempt();

        SriReceptionResult result =
                new SriReceptionResult(
                        SriReceptionStatus.RETURNED,
                        List.of()
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenReturn(
                result
        );

        SriReceptionResult returned =
                service.submit(
                        DOCUMENT_ID
                );

        assertSame(
                result,
                returned
        );

        verify(
                markDocumentRejectedUseCase
        ).markRejected(
                DOCUMENT_ID
        );

        verify(
                scheduleDocumentRetryUseCase,
                never()
        ).scheduleRetry(
                DOCUMENT_ID
        );
    }

    @Test
    void shouldScheduleRetryWhenSriReceptionFails() {

        stubStartedAttempt();

        SriReceptionException failure =
                new SriReceptionException(
                        "SRI unavailable"
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenThrow(
                failure
        );

        SriReceptionException thrown =
                assertThrows(
                        SriReceptionException.class,
                        () ->
                                service.submit(
                                        DOCUMENT_ID
                                )
                );

        assertSame(
                failure,
                thrown
        );

        verify(
                markDocumentSubmittedUseCase
        ).markSubmitted(
                DOCUMENT_ID
        );

        verify(
                scheduleDocumentRetryUseCase
        ).scheduleRetry(
                DOCUMENT_ID
        );

        verify(
                markDocumentRejectedUseCase,
                never()
        ).markRejected(
                DOCUMENT_ID
        );
    }


    @Test
    void shouldPersistReceivedAttemptBeforeReturning() {

        SriReceptionAttempt attempt =
                new SriReceptionAttempt(
                        DOCUMENT_ID,
                        1,
                        NOW
                );

        when(
                sriReceptionAttemptPort.start(
                        DOCUMENT_ID,
                        NOW
                )
        ).thenReturn(attempt);

        SriReceptionResult response =
                new SriReceptionResult(
                        SriReceptionStatus.RECEIVED,
                        List.of()
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenReturn(response);

        SriReceptionResult result =
                service.submit(DOCUMENT_ID);

        assertSame(response, result);

        InOrder order = inOrder(
                markDocumentSubmittedUseCase,
                sriReceptionAttemptPort,
                submitSignedDocumentToSriUseCase
        );

        order.verify(
                markDocumentSubmittedUseCase
        ).markSubmitted(DOCUMENT_ID);

        order.verify(
                sriReceptionAttemptPort
        ).start(DOCUMENT_ID, NOW);

        order.verify(
                submitSignedDocumentToSriUseCase
        ).submit(DOCUMENT_ID);

        order.verify(
                sriReceptionAttemptPort
        ).complete(
                DOCUMENT_ID,
                1,
                SriReceptionAttemptResult.RECEIVED,
                null,
                null,
                NOW
        );

        verifyNoInteractions(
                markDocumentRejectedUseCase,
                scheduleDocumentRetryUseCase
        );
    }

    @Test
    void shouldPersistTechnicalFailureBeforeRetry() {

        SriReceptionAttempt attempt =
                new SriReceptionAttempt(
                        DOCUMENT_ID,
                        1,
                        NOW
                );

        when(
                sriReceptionAttemptPort.start(
                        DOCUMENT_ID,
                        NOW
                )
        ).thenReturn(attempt);

        SriReceptionException failure =
                new SriReceptionException(
                        "SRI unavailable"
                );

        when(
                submitSignedDocumentToSriUseCase.submit(
                        DOCUMENT_ID
                )
        ).thenThrow(failure);

        SriReceptionException thrown =
                assertThrows(
                        SriReceptionException.class,
                        () -> service.submit(DOCUMENT_ID)
                );

        assertSame(failure, thrown);

        InOrder order = inOrder(
                markDocumentSubmittedUseCase,
                sriReceptionAttemptPort,
                submitSignedDocumentToSriUseCase,
                scheduleDocumentRetryUseCase
        );

        order.verify(
                markDocumentSubmittedUseCase
        ).markSubmitted(DOCUMENT_ID);

        order.verify(
                sriReceptionAttemptPort
        ).start(DOCUMENT_ID, NOW);

        order.verify(
                submitSignedDocumentToSriUseCase
        ).submit(DOCUMENT_ID);

        order.verify(
                sriReceptionAttemptPort
        ).complete(
                DOCUMENT_ID,
                1,
                SriReceptionAttemptResult.TECHNICAL_ERROR,
                "SRI_RECEPTION_ERROR",
                "SRI unavailable",
                NOW
        );

        order.verify(
                scheduleDocumentRetryUseCase
        ).scheduleRetry(DOCUMENT_ID);
    }

    private void stubStartedAttempt() {

        when(
                sriReceptionAttemptPort.start(
                        DOCUMENT_ID,
                        NOW
                )
        ).thenReturn(
                new SriReceptionAttempt(
                        DOCUMENT_ID,
                        1,
                        NOW
                )
        );
    }
}