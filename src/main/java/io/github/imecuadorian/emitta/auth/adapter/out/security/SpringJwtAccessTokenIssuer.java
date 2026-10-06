package io.github.imecuadorian.emitta.auth.adapter.out.security;

import io.github.imecuadorian.emitta.auth.application.model.AuthenticatedApiClient;
import io.github.imecuadorian.emitta.auth.application.model.IssuedAccessToken;
import io.github.imecuadorian.emitta.auth.application.port.out.AccessTokenIssuer;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SpringJwtAccessTokenIssuer
        implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    private final String issuer;
    private final String audience;

    private final Duration ttl;

    public SpringJwtAccessTokenIssuer(
            JwtEncoder jwtEncoder,
            Clock clock,
            String issuer,
            String audience,
            Duration ttl
    ) {
        this.jwtEncoder =
                Objects.requireNonNull(jwtEncoder);

        this.clock =
                Objects.requireNonNull(clock);

        this.issuer =
                Objects.requireNonNull(issuer);

        this.audience =
                Objects.requireNonNull(audience);

        this.ttl =
                Objects.requireNonNull(ttl);
    }

    @Override
    public IssuedAccessToken issue(
            AuthenticatedApiClient client
    ) {

        Instant issuedAt =
                clock.instant();

        Instant expiresAt =
                issuedAt.plus(
                        ttl
                );

        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(
                                issuer
                        )
                        .subject(
                                client.clientId()
                        )
                        .audience(
                                List.of(
                                        audience
                                )
                        )
                        .issuedAt(
                                issuedAt
                        )
                        .expiresAt(
                                expiresAt
                        )
                        .id(
                                UUID.randomUUID()
                                        .toString()
                        )
                        .claim(
                                "client_id",
                                client.clientId()
                        )
                        .claim(
                                "tenant_id",
                                client.tenantId()
                                        .toString()
                        )
                        .claim(
                                "scope",
                                String.join(
                                        " ",
                                        client.scopes()
                                )
                        )
                        .build();

        JwsHeader header =
                JwsHeader.with(
                                SignatureAlgorithm.RS256
                        )
                        .build();

        String token =
                jwtEncoder
                        .encode(
                                JwtEncoderParameters.from(
                                        header,
                                        claims
                                )
                        )
                        .getTokenValue();

        return new IssuedAccessToken(
                token,
                ttl.toSeconds()
        );
    }
}