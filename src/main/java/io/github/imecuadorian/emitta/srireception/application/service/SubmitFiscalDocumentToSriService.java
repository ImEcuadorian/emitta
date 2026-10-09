package io.github.imecuadorian.emitta.srireception.application.service;

import io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttempt;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttemptResult;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionMessage;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentRejectedUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.MarkDocumentSubmittedUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.ScheduleDocumentRetryUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitFiscalDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.in.SubmitSignedDocumentToSriUseCase;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionAttemptPort;
import io.github.imecuadorian.emitta.srireception.domain.SriReceptionStatus;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class SubmitFiscalDocumentToSriService
        implements SubmitFiscalDocumentToSriUseCase {

    private final MarkDocumentSubmittedUseCase
            markDocumentSubmittedUseCase;

    private final SubmitSignedDocumentToSriUseCase
            submitSignedDocumentToSriUseCase;

    private final MarkDocumentRejectedUseCase
            markDocumentRejectedUseCase;

    private final ScheduleDocumentRetryUseCase
            scheduleDocumentRetryUseCase;

    private final SriReceptionAttemptPort
            sriReceptionAttemptPort;

    private final Clock clock;

    public SubmitFiscalDocumentToSriService(
            MarkDocumentSubmittedUseCase markDocumentSubmittedUseCase,
            SubmitSignedDocumentToSriUseCase submitSignedDocumentToSriUseCase,
            MarkDocumentRejectedUseCase markDocumentRejectedUseCase,
            ScheduleDocumentRetryUseCase scheduleDocumentRetryUseCase,
            SriReceptionAttemptPort sriReceptionAttemptPort,
            Clock clock
    ) {

        this.markDocumentSubmittedUseCase =
                Objects.requireNonNull(markDocumentSubmittedUseCase);

        this.submitSignedDocumentToSriUseCase =
                Objects.requireNonNull(submitSignedDocumentToSriUseCase);

        this.markDocumentRejectedUseCase =
                Objects.requireNonNull(markDocumentRejectedUseCase);

        this.scheduleDocumentRetryUseCase =
                Objects.requireNonNull(scheduleDocumentRetryUseCase);

        this.sriReceptionAttemptPort =
                Objects.requireNonNull(sriReceptionAttemptPort);

        this.clock =
                Objects.requireNonNull(clock);
    }

    @Override
    public SriReceptionResult submit(UUID documentId) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        /*
         * Persist the submission state before external I/O.
         */
        markDocumentSubmittedUseCase.markSubmitted(
                documentId
        );

        /*
         * Persist the attempt before contacting SRI.
         */
        SriReceptionAttempt attempt =
                sriReceptionAttemptPort.start(
                        documentId,
                        clock.instant()
                );

        SriReceptionResult result;

        try {

            result =
                    Objects.requireNonNull(
                            submitSignedDocumentToSriUseCase.submit(
                                    documentId
                            ),
                            "SRI reception result cannot be null"
                    );

        } catch (SriReceptionException exception) {

            /*
             * The remote outcome is unknown (including timeout after acceptance).
             * Keep SUBMITTED for reconciliation by access key; never auto-resend.
             */
            try {

                sriReceptionAttemptPort.complete(
                        documentId,
                        attempt.attemptNumber(),
                        SriReceptionAttemptResult.TECHNICAL_ERROR,
                        "SRI_RECEPTION_ERROR",
                        exception.getMessage(),
                        clock.instant()
                );

            } catch (RuntimeException persistenceFailure) {

                exception.addSuppressed(
                        persistenceFailure
                );

                throw exception;
            }

            throw exception;
        }

        if (
                result.status()
                        == SriReceptionStatus.RECEIVED
        ) {

            sriReceptionAttemptPort.complete(
                    documentId,
                    attempt.attemptNumber(),
                    SriReceptionAttemptResult.RECEIVED,
                    null,
                    null,
                    clock.instant()
            );

            /*
             * SUBMITTED remains the document state.
             * Reception is not fiscal authorization.
             */
            return result;
        }

        if (
                result.status()
                        == SriReceptionStatus.RETURNED
        ) {

            SriReceptionMessage firstMessage =
                    result.messages().isEmpty()
                            ? null
                            : result.messages().getFirst();

            sriReceptionAttemptPort.complete(
                    documentId,
                    attempt.attemptNumber(),
                    SriReceptionAttemptResult.RETURNED,
                    firstMessage == null
                            ? null
                            : firstMessage.identifier(),
                    firstMessage == null
                            ? null
                            : firstMessage.message(),
                    clock.instant()
            );

            markDocumentRejectedUseCase.markRejected(
                    documentId
            );

            return result;
        }

        throw new IllegalStateException(
                "Unsupported SRI reception status: "
                        + result.status()
        );
    }
}
