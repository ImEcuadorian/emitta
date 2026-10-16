package io.github.imecuadorian.emitta.auth.adapter.in.web;

import io.github.imecuadorian.emitta.auth.application.port.out.AccessTokenIssuer;
import io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAuthenticationPort;
import io.github.imecuadorian.emitta.auth.adapter.in.web.model.TokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Private operator access; customer client credentials retain their existing contract. */
@RestController
@RequestMapping("/api/v1/operator")
public final class OperatorLoginController {
    private final OperatorPersonalAccess access;
    private final ApiClientAuthenticationPort clients;
    private final AccessTokenIssuer tokens;

    public OperatorLoginController(OperatorPersonalAccess access, ApiClientAuthenticationPort clients, AccessTokenIssuer tokens) {
        this.access = access;
        this.clients = clients;
        this.tokens = tokens;
    }

    public record Credentials(@NotBlank @Email @Size(max=254) String email,
                              @NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "Credentials[REDACTED]"; }
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody Credentials credentials) {
        String clientId = access.authenticate(credentials.email(), credentials.password());
        var client = clientId == null ? java.util.Optional.<io.github.imecuadorian.emitta.auth.application.model.AuthenticatedApiClient>empty()
                : clients.findByClientId(clientId).filter(c -> c.active() && c.scopes().contains("platform:admin"));
        if (client.isEmpty()) return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        var token = tokens.issue(client.get());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(new TokenResponse(token.token(), "Bearer", token.expiresInSeconds()));
    }

    @PostMapping("/personal-access")
    public ResponseEntity<Void> configure(@AuthenticationPrincipal Jwt actor, @Valid @RequestBody Credentials credentials) {
        access.configure(actor.getSubject(), credentials.email(), credentials.password());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
