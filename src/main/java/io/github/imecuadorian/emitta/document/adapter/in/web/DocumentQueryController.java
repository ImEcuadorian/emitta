package io.github.imecuadorian.emitta.document.adapter.in.web;
import io.github.imecuadorian.emitta.document.application.port.in.ReadDocumentUseCase;
import io.github.imecuadorian.emitta.document.application.exception.DocumentUnavailableException;
import io.github.imecuadorian.emitta.document.domain.DocumentStatus;
import io.github.imecuadorian.emitta.documentartifact.application.port.in.LoadDocumentArtifactUseCase;
import io.github.imecuadorian.emitta.documentartifact.domain.DocumentArtifactType;
import io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactNotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/documents")
public final class DocumentQueryController {
    private final ReadDocumentUseCase documents;
    private final io.github.imecuadorian.emitta.recovery.DurableWorkPort jobs;
    private final ObjectProvider<LoadDocumentArtifactUseCase> loader;
    public DocumentQueryController(ReadDocumentUseCase documents,ObjectProvider<LoadDocumentArtifactUseCase> loader,io.github.imecuadorian.emitta.recovery.DurableWorkPort jobs) {
        this.documents=documents;this.loader=loader;this.jobs=jobs;
    }
    public record DocumentView(UUID id,DocumentStatus status,String accessKey,Long sequential,
                               String environment,Instant issuedAt,Instant authorizedAt) {}
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentView> get(JwtAuthenticationToken jwt,@PathVariable UUID documentId) {
        var d=documents.read(tenant(jwt),documentId);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new DocumentView(d.getId(),d.getStatus(),
                d.getAccessKey(),d.getSequential(),d.getEnvironment().name(),d.getIssuedAt(),d.getAuthorizedAt()));
    }
    @GetMapping("/{documentId}/artifacts/{type}")
    public ResponseEntity<byte[]> artifact(JwtAuthenticationToken jwt,@PathVariable UUID documentId,@PathVariable String type) {
        var d=documents.read(tenant(jwt),documentId); // ownership BEFORE any object storage access
        var kind=switch(type) {
            case "authorized-xml" -> DocumentArtifactType.AUTHORIZED_XML;
            case "ride-pdf" -> DocumentArtifactType.RIDE_PDF;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Artifact unavailable");
        };
        if(d.getStatus()!=DocumentStatus.AUTHORIZED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Document not authorized");
        var service=loader.getIfAvailable();
        if(service==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Artifact access disabled");
        var loaded=service.load(documentId,kind); // validates persisted SHA-256 before download
        String extension=kind==DocumentArtifactType.RIDE_PDF?"pdf":"xml";
        return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment().filename(documentId+"."+extension).build().toString())
                .eTag(loaded.artifact().sha256())
                .header("Content-Type",kind==DocumentArtifactType.RIDE_PDF?"application/pdf":"application/xml")
                .body(loaded.content());
    }
    public record RideJobView(String state,int attempts,java.time.Instant nextAttemptAt,String failureCategory) {}
    @GetMapping("/{documentId}/ride-job")
    public ResponseEntity<RideJobView> rideJob(JwtAuthenticationToken jwt,@PathVariable UUID documentId) {
        documents.read(tenant(jwt),documentId);
        var job=jobs.find(tenant(jwt),documentId,io.github.imecuadorian.emitta.recovery.DurableWorkPort.Kind.RIDE)
            .orElseThrow(()->new DocumentUnavailableException());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new RideJobView(job.state(),job.attempts(),job.nextAttemptAt(),job.failureCategory()));
    }
    private static UUID tenant(JwtAuthenticationToken jwt) { return UUID.fromString(jwt.getToken().getClaimAsString("tenant_id")); }
    @ExceptionHandler({DocumentUnavailableException.class,DocumentArtifactNotFoundException.class})
    ResponseEntity<org.springframework.http.ProblemDetail> unavailable() {
        return ResponseEntity.status(404).body(org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"Resource unavailable"));
    }
}
