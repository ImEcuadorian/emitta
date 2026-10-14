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
public final class DocumentQueryController {
    private final ReadDocumentUseCase documents;
    private final io.github.imecuadorian.emitta.recovery.DurableWorkPort jobs;
    private final ObjectProvider<LoadDocumentArtifactUseCase> loader;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    public DocumentQueryController(ReadDocumentUseCase documents,ObjectProvider<LoadDocumentArtifactUseCase> loader,io.github.imecuadorian.emitta.recovery.DurableWorkPort jobs,org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.documents=documents;this.loader=loader;this.jobs=jobs;this.jdbc=jdbc;
    }
    public record DocumentView(UUID id,DocumentStatus status,String accessKey,Long sequential,
                               String environment,Instant issuedAt,Instant authorizedAt) {}
    @GetMapping("/api/v1/documents/{documentId}")
    public ResponseEntity<DocumentView> get(JwtAuthenticationToken jwt,@PathVariable UUID documentId) {
        var d=documents.read(tenant(jwt),documentId);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new DocumentView(d.getId(),d.getStatus(),
                d.getAccessKey(),d.getSequential(),d.getEnvironment().name(),d.getIssuedAt(),d.getAuthorizedAt()));
    }
    @GetMapping("/api/v1/documents/{documentId}/artifacts/{type}")
    public ResponseEntity<byte[]> artifact(JwtAuthenticationToken jwt,@PathVariable UUID documentId,@PathVariable String type) {
        return loadArtifact(tenant(jwt),documentId,type);
    }
    private ResponseEntity<byte[]> loadArtifact(UUID tenantId,UUID documentId,String type) {
        var d=documents.read(tenantId,documentId); // ownership BEFORE any object storage access
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
    @GetMapping("/api/v1/documents/{documentId}/ride-job")
    public ResponseEntity<RideJobView> rideJob(JwtAuthenticationToken jwt,@PathVariable UUID documentId) {
        documents.read(tenant(jwt),documentId);
        var job=jobs.find(tenant(jwt),documentId,io.github.imecuadorian.emitta.recovery.DurableWorkPort.Kind.RIDE)
            .orElseThrow(()->new DocumentUnavailableException());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new RideJobView(job.state(),job.attempts(),job.nextAttemptAt(),job.failureCategory()));
    }
    @GetMapping("/api/v1/tenants/{tenantId}/documents")
    public ResponseEntity<java.util.List<java.util.Map<String,Object>>> list(@PathVariable UUID tenantId,
            @RequestParam(defaultValue="25") int limit,@RequestParam(defaultValue="0") int offset) {
        if(limit<1||limit>100||offset<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");
        var rows=jdbc.sql("""
            select d.id,d.status,d.environment,d.access_key as "accessKey",d.sequential,
              d.issued_at as "issuedAt",d.authorized_at as "authorizedAt",e.code as "establishmentCode",
              p.code as "pointOfIssueCode",
              exists(select 1 from emitta.document_artifacts a where a.document_id=d.id and a.artifact_type='RIDE_PDF') as "pdfAvailable",
              exists(select 1 from emitta.document_artifacts a where a.document_id=d.id and a.artifact_type='AUTHORIZED_XML') as "xmlAvailable"
            from emitta.documents d join emitta.points_of_issue p on p.id=d.point_of_issue_id
              join emitta.establishments e on e.id=p.establishment_id
            where d.tenant_id=:tenant order by d.created_at desc,d.id limit :limit offset :offset
            """).param("tenant",tenantId).param("limit",limit).param("offset",offset)
                .query(new org.springframework.jdbc.core.ColumnMapRowMapper()).list();
        for(var row:rows)for(var key:java.util.List.of("issuedAt","authorizedAt"))
            if(row.get(key) instanceof java.sql.Timestamp stamp)row.put(key,stamp.toInstant().toString());
        io.github.imecuadorian.emitta.auth.adapter.out.persistence.AdministrationAuditRecorder.record(jdbc,tenantId,tenantId,"DOCUMENTS_READ");
        return ResponseEntity.ok().header("Cache-Control","no-store").body(rows);
    }
    @GetMapping("/api/v1/tenants/{tenantId}/documents/{documentId}/artifacts/{type}")
    public ResponseEntity<byte[]> operatorArtifact(@PathVariable UUID tenantId,@PathVariable UUID documentId,@PathVariable String type) {
        var result=loadArtifact(tenantId,documentId,type);
        io.github.imecuadorian.emitta.auth.adapter.out.persistence.AdministrationAuditRecorder.record(jdbc,tenantId,documentId,"ARTIFACT_DOWNLOADED");
        return result;
    }
    private static UUID tenant(JwtAuthenticationToken jwt) { return UUID.fromString(jwt.getToken().getClaimAsString("tenant_id")); }
    @ExceptionHandler({DocumentUnavailableException.class,DocumentArtifactNotFoundException.class})
    ResponseEntity<org.springframework.http.ProblemDetail> unavailable() {
        return ResponseEntity.status(404).body(org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"Resource unavailable"));
    }
}
