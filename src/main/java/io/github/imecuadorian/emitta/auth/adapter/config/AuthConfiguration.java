package io.github.imecuadorian.emitta.auth.adapter.config;

import io.github.imecuadorian.emitta.auth.adapter.out.security.BCryptClientSecretVerifier;
import io.github.imecuadorian.emitta.auth.adapter.out.security.SpringJwtAccessTokenIssuer;
import io.github.imecuadorian.emitta.auth.application.port.in.IssueAccessTokenUseCase;
import io.github.imecuadorian.emitta.auth.application.port.out.ApiClientAuthenticationPort;
import io.github.imecuadorian.emitta.auth.application.port.out.ClientSecretVerifier;
import io.github.imecuadorian.emitta.auth.application.service.IssueAccessTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AuthConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(
                12
        );
    }

    @Bean
    ClientSecretVerifier clientSecretVerifier(
            PasswordEncoder passwordEncoder
    ) {

        return new BCryptClientSecretVerifier(
                passwordEncoder
        );
    }

    @Bean
    SpringJwtAccessTokenIssuer accessTokenIssuer(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value(
                    "${emitta.security.jwt.issuer}"
            )
            String issuer,
            @Value(
                    "${emitta.security.jwt.audience}"
            )
            String audience,
            @Value(
                    "${emitta.security.jwt.ttl:PT15M}"
            )
            Duration ttl
    ) {

        return new SpringJwtAccessTokenIssuer(
                jwtEncoder,
                clock,
                issuer,
                audience,
                ttl
        );
    }

    @Bean
    IssueAccessTokenUseCase issueAccessTokenUseCase(
            ApiClientAuthenticationPort apiClientPort,
            ClientSecretVerifier secretVerifier,
            SpringJwtAccessTokenIssuer tokenIssuer,
            Clock clock
    ) {

        return new IssueAccessTokenService(
                apiClientPort,
                secretVerifier,
                tokenIssuer,
                clock
        );
    }
}