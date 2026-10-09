package io.github.imecuadorian.emitta.invoice.adapter.in.web;

import io.github.imecuadorian.emitta.document.application.exception.DocumentEnvironmentDisabledException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentFiscalResourceInactiveException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentFiscalResourceNotFoundException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentIdempotencyConflictException;
import io.github.imecuadorian.emitta.document.application.exception.DocumentTenantMismatchException;
import io.github.imecuadorian.emitta.invoice.application.exception.ExpectedTotalMismatchException;
import io.github.imecuadorian.emitta.invoice.application.exception.InvoiceIdempotencyConflictException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(
        assignableTypes =
                InvoiceController.class
)
public class InvoiceExceptionHandler {

    @ExceptionHandler({
            DocumentIdempotencyConflictException.class,
            InvoiceIdempotencyConflictException.class
    })
    ResponseEntity<ProblemDetail> idempotencyConflict(
            RuntimeException exception
    ) {

        return response(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                exception.getMessage()
        );
    }

    @ExceptionHandler(
            ExpectedTotalMismatchException.class
    )
    ResponseEntity<ProblemDetail> expectedTotalMismatch(
            ExpectedTotalMismatchException exception
    ) {

        ProblemDetail problem =
                problem(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "EXPECTED_TOTAL_MISMATCH",
                        exception.getMessage()
                );

        problem.setProperty(
                "expected",
                exception.getExpected()
        );

        problem.setProperty(
                "calculated",
                exception.getCalculated()
        );

        return ResponseEntity
                .status(
                        HttpStatus.UNPROCESSABLE_ENTITY
                )
                .body(
                        problem
                );
    }

    @ExceptionHandler({
            DocumentFiscalResourceNotFoundException.class,
            io.github.imecuadorian.emitta.invoice.application.exception.InvoiceCustomerUnavailableException.class,
            DocumentTenantMismatchException.class
    })
    ResponseEntity<ProblemDetail> fiscalResourceNotFound(
            RuntimeException exception
    ) {

        /*
         * Tenant mismatch is intentionally exposed as 404.
         * The API must not reveal ownership of another tenant's
         * point of issue.
         */
        return response(
                HttpStatus.NOT_FOUND,
                "FISCAL_RESOURCE_NOT_FOUND",
                "Fiscal resource was not found"
        );
    }

    @ExceptionHandler({
            DocumentFiscalResourceInactiveException.class,
            DocumentEnvironmentDisabledException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> fiscalValidation(
            RuntimeException exception
    ) {

        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FISCAL_VALIDATION_ERROR",
                exception.getMessage()
        );
    }

    @ExceptionHandler(
            InvalidTenantContextException.class
    )
    ResponseEntity<ProblemDetail> invalidTenantContext(
            InvalidTenantContextException exception
    ) {

        return response(
                HttpStatus.FORBIDDEN,
                "INVALID_TENANT_CONTEXT",
                exception.getMessage()
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ProblemDetail> badRequest(
            Exception exception
    ) {

        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request validation failed"
        );
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String code,
            String detail
    ) {

        return ResponseEntity
                .status(
                        status
                )
                .body(
                        problem(
                                status,
                                code,
                                detail
                        )
                );
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String detail
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail == null
                                ? status.getReasonPhrase()
                                : detail
                );

        problem.setTitle(
                status.getReasonPhrase()
        );

        problem.setProperty(
                "code",
                code
        );

        return problem;
    }
}