package io.github.imecuadorian.emitta.srireception.adapter.out.persistence;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttempt;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionAttemptResult;
import io.github.imecuadorian.emitta.srireception.application.port.out.SriReceptionAttemptPort;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public final class PostgreSqlSriReceptionAttemptAdapter
        implements SriReceptionAttemptPort {

    private static final String OPERATION =
            "SRI_SUBMISSION";

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    public PostgreSqlSriReceptionAttemptAdapter(
            JdbcClient jdbcClient,
            TransactionTemplate transactionTemplate
    ) {

        this.jdbcClient =
                Objects.requireNonNull(
                        jdbcClient
                );

        this.transactionTemplate =
                Objects.requireNonNull(
                        transactionTemplate
                );
    }

    @Override
    public SriReceptionAttempt start(
            UUID documentId,
            Instant startedAt
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                startedAt,
                "Attempt start time cannot be null"
        );

        SriReceptionAttempt attempt =
                transactionTemplate.execute(
                        status -> {

                            lockDocument(
                                    documentId
                            );

                            /*
                             * Fail closed until SRI reconciliation is implemented.
                             * A previous attempt may have reached SRI even when
                             * Emitta did not receive the response.
                             */
                            assertNoPreviousSubmission(
                                    documentId
                            );

                            int nextAttemptNumber =
                                    nextAttemptNumber(
                                            documentId
                                    );

                            int inserted =
                                    jdbcClient.sql(
                                                    """
                                                    INSERT INTO emitta.document_attempts (
                                                        document_id,
                                                        attempt_number,
                                                        operation,
                                                        started_at
                                                    )
                                                    VALUES (
                                                        :documentId,
                                                        :attemptNumber,
                                                        :operation,
                                                        :startedAt
                                                    )
                                                    """
                                            )
                                            .param(
                                                    "documentId",
                                                    documentId
                                            )
                                            .param(
                                                    "attemptNumber",
                                                    nextAttemptNumber
                                            )
                                            .param(
                                                    "operation",
                                                    OPERATION
                                            )
                                            .param(
                                                    "startedAt",
                                                    startedAt.atOffset(
                                                            ZoneOffset.UTC
                                                    )
                                            )
                                            .update();

                            if (inserted != 1) {

                                throw new IllegalStateException(
                                        "Unable to create SRI reception attempt"
                                );
                            }

                            return new SriReceptionAttempt(
                                    documentId,
                                    nextAttemptNumber,
                                    startedAt
                            );
                        }
                );

        return Objects.requireNonNull(
                attempt,
                "SRI reception attempt transaction returned null"
        );
    }

    private void assertNoPreviousSubmission(
            UUID documentId
    ) {

        Boolean previousAttemptExists =
                jdbcClient.sql(
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM emitta.document_attempts
                                    WHERE document_id = :documentId
                                      AND operation = :operation
                                )
                                """
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .param(
                                "operation",
                                OPERATION
                        )
                        .query(
                                Boolean.class
                        )
                        .single();

        if (
                Boolean.TRUE.equals(
                        previousAttemptExists
                )
        ) {

            throw new IllegalStateException(
                    "SRI submission requires reconciliation "
                            + "before another attempt for document: "
                            + documentId
            );
        }
    }

    @Override
    public void complete(
            UUID documentId,
            int attemptNumber,
            SriReceptionAttemptResult result,
            String errorCode,
            String errorMessage,
            Instant finishedAt
    ) {

        Objects.requireNonNull(
                documentId,
                "Document id cannot be null"
        );

        Objects.requireNonNull(
                result,
                "Attempt result cannot be null"
        );

        Objects.requireNonNull(
                finishedAt,
                "Attempt finish time cannot be null"
        );

        if (attemptNumber < 1) {

            throw new IllegalArgumentException(
                    "Attempt number must be positive"
            );
        }

        int updated =
                jdbcClient.sql(
                                """
                                UPDATE emitta.document_attempts
                                SET
                                    result = :result,
                                    error_code = :errorCode,
                                    error_message = :errorMessage,
                                    finished_at = :finishedAt
                                WHERE document_id = :documentId
                                  AND operation = :operation
                                  AND attempt_number = :attemptNumber
                                  AND finished_at IS NULL
                                """
                        )
                        .param(
                                "result",
                                result.name()
                        )
                        .param(
                                "errorCode",
                                errorCode
                        )
                        .param(
                                "errorMessage",
                                errorMessage
                        )
                        .param(
                                "finishedAt",
                                finishedAt.atOffset(
                                        ZoneOffset.UTC
                                )
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .param(
                                "operation",
                                OPERATION
                        )
                        .param(
                                "attemptNumber",
                                attemptNumber
                        )
                        .update();

        if (updated == 1) {

            return;
        }

        String existingResult =
                jdbcClient.sql(
                                """
                                SELECT result
                                FROM emitta.document_attempts
                                WHERE document_id = :documentId
                                  AND operation = :operation
                                  AND attempt_number = :attemptNumber
                                """
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .param(
                                "operation",
                                OPERATION
                        )
                        .param(
                                "attemptNumber",
                                attemptNumber
                        )
                        .query(
                                String.class
                        )
                        .optional()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "SRI reception attempt not found"
                                        )
                        );

        /*
         * Idempotent completion.
         */
        if (
                result.name()
                        .equals(
                                existingResult
                        )
        ) {

            return;
        }

        throw new IllegalStateException(
                "SRI reception attempt was already completed with result "
                        + existingResult
        );
    }

    private void lockDocument(
            UUID documentId
    ) {

        jdbcClient.sql(
                        """
                        SELECT id
                        FROM emitta.documents
                        WHERE id = :documentId
                        FOR UPDATE
                        """
                )
                .param(
                        "documentId",
                        documentId
                )
                .query(
                        UUID.class
                )
                .optional()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Document not found: "
                                                + documentId
                                )
                );
    }

    private int nextAttemptNumber(
            UUID documentId
    ) {

        Integer next =
                jdbcClient.sql(
                                """
                                SELECT
                                    COALESCE(
                                        MAX(attempt_number),
                                        0
                                    ) + 1
                                FROM emitta.document_attempts
                                WHERE document_id = :documentId
                                  AND operation = :operation
                                """
                        )
                        .param(
                                "documentId",
                                documentId
                        )
                        .param(
                                "operation",
                                OPERATION
                        )
                        .query(
                                Integer.class
                        )
                        .single();

        return Objects.requireNonNull(
                next,
                "Next SRI attempt number cannot be null"
        );
    }
}