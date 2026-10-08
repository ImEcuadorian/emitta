package io.github.imecuadorian.emitta.srireception.application.port.out;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttempt;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttemptResult;

import java.time.Instant;
import java.util.UUID;

public interface SriReceptionAttemptPort {

    SriReceptionAttempt start(
            UUID documentId,
            Instant startedAt
    );

    void complete(
            UUID documentId,
            int attemptNumber,
            SriReceptionAttemptResult result,
            String errorCode,
            String errorMessage,
            Instant finishedAt
    );
}