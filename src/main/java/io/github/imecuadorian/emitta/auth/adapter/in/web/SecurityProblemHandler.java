package io.github.imecuadorian.emitta.auth.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public final class SecurityProblemHandler
        implements AuthenticationEntryPoint,
        AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        writeProblem(
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required or the access token is invalid"
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        writeProblem(
                response,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "The authenticated client does not have permission to perform this operation"
        );
    }

    private static void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String detail
    ) throws IOException {

        response.setStatus(
                status.value()
        );

        response.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        String body =
                """
                {
                  "type": "about:blank",
                  "title": "%s",
                  "status": %d,
                  "detail": "%s",
                  "code": "%s"
                }
                """.formatted(
                        status.getReasonPhrase(),
                        status.value(),
                        detail,
                        code
                );

        response.getWriter()
                .write(
                        body
                );
    }
}