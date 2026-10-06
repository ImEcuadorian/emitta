package io.github.imecuadorian.emitta.auth.application.service;

import io.github.imecuadorian.emitta.auth.application.command.IssueAccessTokenCommand;
import io.github.imecuadorian.emitta.auth.application.exception.InvalidClientCredentialsException;
import io.github.imecuadorian.emitta.auth.application.model.AuthenticatedApiClient;
import io.github.imecuadorian.emitta.auth.application.model.IssuedAccessToken;
import io.github.imecuadorian.emitta.auth.application.port.in.IssueAccessTokenUseCase;
import io.github.imecuadorian.emitta.auth.application.port.out.AccessTokenIssuer;
import io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAuthenticationPort;
import io.github.imecuadorian.emitta.auth.application.port.out.ClientSecretVerifier;

import java.time.Clock;
import java.util.Objects;

public final class IssueAccessTokenService
        implements IssueAccessTokenUseCase {

    private final ApiClientAuthenticationPort apiClientPort;
    private final ClientSecretVerifier secretVerifier;
    private final AccessTokenIssuer tokenIssuer;
    private final Clock clock;

    public IssueAccessTokenService(
            ApiClientAuthenticationPort apiClientPort,
            ClientSecretVerifier secretVerifier,
            AccessTokenIssuer tokenIssuer,
            Clock clock
    ) {
        this.apiClientPort =
                Objects.requireNonNull(apiClientPort);

        this.secretVerifier =
                Objects.requireNonNull(secretVerifier);

        this.tokenIssuer =
                Objects.requireNonNull(tokenIssuer);

        this.clock =
                Objects.requireNonNull(clock);
    }

    @Override
    public IssuedAccessToken issue(
            IssueAccessTokenCommand command
    ) {

        Objects.requireNonNull(
                command,
                "Issue access token command cannot be null"
        );

        AuthenticatedApiClient client =
                apiClientPort
                        .findByClientId(
                                command.clientId()
                        )
                        .orElseThrow(
                                InvalidClientCredentialsException::new
                        );

        if (
                !client.active()
                        || !secretVerifier.matches(
                        command.clientSecret(),
                        client.secretHash()
                )
        ) {
            throw new InvalidClientCredentialsException();
        }

        IssuedAccessToken token =
                tokenIssuer.issue(
                        client
                );

        apiClientPort.updateLastUsedAt(
                client.id(),
                clock.instant()
        );

        return token;
    }
}