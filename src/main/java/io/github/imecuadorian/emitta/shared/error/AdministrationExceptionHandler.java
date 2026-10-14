package io.github.imecuadorian.emitta.shared.error;

import io.github.imecuadorian.emitta.auth.adapter.in.web.ApiClientAdministrationController;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.in.web.CertificateAdministrationController;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Resolve administrative errors directly, without exposing upload contents or container error pages. */
@RestControllerAdvice(assignableTypes={ApiClientAdministrationController.class,CertificateAdministrationController.class})
public final class AdministrationExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException exception) {
        var problem=ProblemDetail.forStatusAndDetail(exception.getStatusCode(),exception.getReason()==null
                ? "Administrative request failed" : exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore()).body(problem);
    }
}
