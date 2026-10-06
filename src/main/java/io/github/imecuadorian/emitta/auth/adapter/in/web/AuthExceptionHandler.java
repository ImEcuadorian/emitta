package io.github.imecuadorian.emitta.auth.adapter.in.web;

import io.github.imecuadorian.emitta.auth.application.exception.InvalidClientCredentialsException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(
        assignableTypes = AuthController.class
)
public class AuthExceptionHandler {

    @ExceptionHandler(
            InvalidClientCredentialsException.class
    )
    ResponseEntity<ProblemDetail> invalidCredentials(
            InvalidClientCredentialsException exception
    ) {

        return response(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CLIENT_CREDENTIALS",
                "Invalid client credentials"
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
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

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail
                );

        problem.setTitle(
                status.getReasonPhrase()
        );

        problem.setProperty(
                "code",
                code
        );

        return ResponseEntity
                .status(status)
                .body(problem);
    }
}