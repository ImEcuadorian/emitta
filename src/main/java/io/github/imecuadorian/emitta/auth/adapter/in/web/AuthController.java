package io.github.imecuadorian.emitta.auth.adapter.in.web;

import io.github.imecuadorian.emitta.auth.adapter.in.web.model.TokenRequest;
import io.github.imecuadorian.emitta.auth.adapter.in.web.model.TokenResponse;
import io.github.imecuadorian.emitta.auth.application.command.IssueAccessTokenCommand;
import io.github.imecuadorian.emitta.auth.application.model.IssuedAccessToken;
import io.github.imecuadorian.emitta.auth.application.port.in.IssueAccessTokenUseCase;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping(
        "/api/v1/auth"
)
public class AuthController {

    private final IssueAccessTokenUseCase
            issueAccessTokenUseCase;

    private final org.springframework.beans.factory.ObjectProvider<io.github.imecuadorian.emitta.ratelimit.RedisTokenBucket> traffic;
    private final io.github.imecuadorian.emitta.ratelimit.RateLimitProperties trafficProperties;
    public AuthController(
            IssueAccessTokenUseCase issueAccessTokenUseCase,
            org.springframework.beans.factory.ObjectProvider<io.github.imecuadorian.emitta.ratelimit.RedisTokenBucket> traffic,
            io.github.imecuadorian.emitta.ratelimit.RateLimitProperties trafficProperties
    ) {
        this.traffic=traffic; this.trafficProperties=trafficProperties;
        this.issueAccessTokenUseCase =
                Objects.requireNonNull(
                        issueAccessTokenUseCase
                );
    }

    @PostMapping(
            "/token"
    )
    public ResponseEntity<TokenResponse> token(
            @Valid
            @RequestBody
            TokenRequest request
    ) {

        var limiter=traffic.getIfAvailable();
        if(limiter!=null) {
            try {
                var decision=limiter.consume(java.util.List.of(new io.github.imecuadorian.emitta.ratelimit.RedisTokenBucket.Bucket(
                    "client-authentication",request.clientId(),trafficProperties.policy("client","authentication"))),"authentication");
                if(!decision.allowed()) return ResponseEntity.status(429).header("Retry-After",Long.toString(decision.retryAfterSeconds())).cacheControl(CacheControl.noStore()).build();
            } catch(io.github.imecuadorian.emitta.ratelimit.RedisTokenBucket.RateLimitUnavailableException unavailable) {
                return ResponseEntity.status(503).header("Retry-After","1").cacheControl(CacheControl.noStore()).build();
            }
        }
        IssuedAccessToken token =
                issueAccessTokenUseCase.issue(
                        new IssueAccessTokenCommand(
                                request.clientId(),
                                request.clientSecret()
                        )
                );

        return ResponseEntity
                .ok()
                .cacheControl(
                        CacheControl.noStore()
                )
                .header(
                        "Pragma",
                        "no-cache"
                )
                .body(
                        new TokenResponse(
                                token.token(),
                                "Bearer",
                                token.expiresInSeconds()
                        )
                );
    }
}