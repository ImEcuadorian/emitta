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

    public AuthController(
            IssueAccessTokenUseCase issueAccessTokenUseCase
    ) {
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