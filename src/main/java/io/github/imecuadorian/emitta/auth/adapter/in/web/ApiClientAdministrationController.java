package io.github.imecuadorian.emitta.auth.adapter.in.web;

import io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAdministrationPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.SecureRandom;
import java.util.*;

/** Platform operator only. Secrets are returned once and are never persisted in plaintext. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/api-clients")
public final class ApiClientAdministrationController {
    private static final Set<String> CUSTOMER_SCOPES = Set.of("invoices:write", "documents:read",
            "artifacts:read", "taxpayers:write", "establishments:write", "points-of-issue:write",
            "certificates:write", "certificates:read");
    private final ApiClientAdministrationPort clients;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    public ApiClientAdministrationController(ApiClientAdministrationPort clients, PasswordEncoder encoder) {
        this.clients=clients; this.encoder=encoder;
    }
    public record CreateRequest(@NotBlank @Size(max=150) String name,
                                @NotEmpty @Size(max=8) Set<@NotBlank @Size(max=100) String> scopes) {}
    public record Credentials(UUID id, UUID tenantId, String clientId, String clientSecret) {
        @Override public String toString() { return "Credentials[id="+id+", secret=REDACTED]"; }
    }
    @GetMapping
    public ResponseEntity<java.util.List<ApiClientAdministrationPort.ClientSummary>> list(@PathVariable UUID tenantId,
            @RequestParam(defaultValue="25") int limit,@RequestParam(defaultValue="0") int offset) {
        if(limit<1 || limit>100 || offset<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(clients.listClients(tenantId,limit,offset));
    }
    @PostMapping
    public ResponseEntity<Credentials> create(@PathVariable UUID tenantId, @Valid @RequestBody CreateRequest request) {
        if (!CUSTOMER_SCOPES.containsAll(request.scopes()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported customer scope");
        UUID id=UUID.randomUUID(); String clientId="emitta_"+id;
        String secret=newSecret();
        clients.create(tenantId,id,clientId,request.name(),encoder.encode(secret),request.scopes());
        return credentials(HttpStatus.CREATED,new Credentials(id,tenantId,clientId,secret));
    }
    @PostMapping("/{id}/rotate-secret")
    public ResponseEntity<Credentials> rotate(@PathVariable UUID tenantId,@PathVariable UUID id) {
        String secret=newSecret();
        String clientId=clients.rotate(tenantId,id,encoder.encode(secret)).orElseThrow(ApiClientAdministrationController::missing);
        return credentials(HttpStatus.OK,new Credentials(id,tenantId,clientId,secret));
    }
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID tenantId,@PathVariable UUID id) {
        if (!clients.revoke(tenantId,id)) throw missing();
        return ResponseEntity.noContent().build();
    }
    private String newSecret() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
        finally { Arrays.fill(bytes,(byte)0); }
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Active client not found"); }
    private static ResponseEntity<Credentials> credentials(HttpStatus status,Credentials value) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(value);
    }
}
