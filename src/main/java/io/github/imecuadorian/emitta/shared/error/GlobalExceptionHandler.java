package io.github.imecuadorian.emitta.shared.error;

import io.github.imecuadorian.emitta.establishment.application.exception.EstablishmentAlreadyExistsException;
import io.github.imecuadorian.emitta.establishment.application.exception.TaxpayerNotFoundException;
import io.github.imecuadorian.emitta.pointofissue.application.exception.EstablishmentNotFoundException;
import io.github.imecuadorian.emitta.pointofissue.application.exception.PointOfIssueAlreadyExistsException;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TaxpayerAlreadyExistsException;
import io.github.imecuadorian.emitta.taxpayer.application.exception.TenantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI VALIDATION_ERROR_TYPE =
            URI.create(
                    "urn:emitta:problem:validation-error"
            );

    private static final URI RESOURCE_NOT_FOUND_TYPE =
            URI.create(
                    "urn:emitta:problem:resource-not-found"
            );

    private static final URI CONFLICT_TYPE =
            URI.create(
                    "urn:emitta:problem:conflict"
            );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {

        List<ValidationError> errors =
                exception
                        .getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                new ValidationError(
                                        error.getField(),
                                        error.getDefaultMessage()
                                )
                        )
                        .toList();

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "One or more request fields are invalid."
                );

        problem.setType(
                VALIDATION_ERROR_TYPE
        );

        problem.setTitle(
                "Request validation failed"
        );

        problem.setInstance(
                URI.create(
                        request.getRequestURI()
                )
        );

        problem.setProperty(
                "errors",
                errors
        );

        return problem;
    }

    @ExceptionHandler(TenantNotFoundException.class)
    ProblemDetail handleTenantNotFound(
            TenantNotFoundException exception,
            HttpServletRequest request
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        exception.getMessage()
                );

        problem.setType(
                RESOURCE_NOT_FOUND_TYPE
        );

        problem.setTitle(
                "Resource not found"
        );

        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        problem.setProperty(
                "tenantId",
                exception.getTenantId()
        );

        return problem;
    }

    @ExceptionHandler(TaxpayerNotFoundException.class)
    ProblemDetail handleTaxpayerNotFound(
            TaxpayerNotFoundException exception,
            HttpServletRequest request
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        exception.getMessage()
                );

        problem.setType(
                RESOURCE_NOT_FOUND_TYPE
        );

        problem.setTitle(
                "Resource not found"
        );

        problem.setInstance(
                URI.create(
                        request.getRequestURI()
                )
        );

        problem.setProperty(
                "taxpayerId",
                exception.getTaxpayerId()
        );

        return problem;
    }

    @ExceptionHandler(EstablishmentAlreadyExistsException.class)
    ProblemDetail handleEstablishmentConflict(
            EstablishmentAlreadyExistsException exception,
            HttpServletRequest request
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );

        problem.setType(
                CONFLICT_TYPE
        );

        problem.setTitle(
                "Resource conflict"
        );

        problem.setInstance(
                URI.create(
                        request.getRequestURI()
                )
        );

        problem.setProperty(
                "code",
                exception.getCode()
        );

        return problem;
    }

    @ExceptionHandler(TaxpayerAlreadyExistsException.class)
    ProblemDetail handleTaxpayerConflict(
            TaxpayerAlreadyExistsException exception,
            HttpServletRequest request
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );

        problem.setType(
                CONFLICT_TYPE
        );

        problem.setTitle(
                "Resource conflict"
        );

        problem.setInstance(
                URI.create(
                        request.getRequestURI()
                )
        );

        problem.setProperty(
                "ruc",
                exception.getRuc()
        );

        return problem;
    }

    @ExceptionHandler(EstablishmentNotFoundException.class)
    ProblemDetail handleEstablishmentNotFound(
            EstablishmentNotFoundException exception,
            HttpServletRequest request
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        exception.getMessage()
                );

        problem.setType(
                RESOURCE_NOT_FOUND_TYPE
        );

        problem.setTitle(
                "Resource not found"
        );

        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        problem.setProperty(
                "establishmentId",
                exception.getEstablishmentId()
        );

        return problem;
    }

    @ExceptionHandler(PointOfIssueAlreadyExistsException.class)
    ProblemDetail handlePointOfIssueConflict(
            PointOfIssueAlreadyExistsException exception,
            HttpServletRequest request
    ) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );

        problem.setType(
                CONFLICT_TYPE
        );

        problem.setTitle(
                "Resource conflict"
        );

        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        problem.setProperty(
                "code",
                exception.getCode()
        );

        return problem;
    }
}