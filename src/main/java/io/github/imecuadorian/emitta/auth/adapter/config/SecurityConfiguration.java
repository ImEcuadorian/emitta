package io.github.imecuadorian.emitta.auth.adapter.config;

import io.github.imecuadorian.emitta.auth.adapter.in.web.SecurityProblemHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProblemHandler problems
    ) throws Exception {

        http
                .csrf(
                        csrf ->
                                csrf.disable()
                )
                .sessionManagement(
                        sessions ->
                                sessions.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )
                .authorizeHttpRequests(
                        authorization ->
                                authorization

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/token"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/openapi/**",
                                                "/v3/api-docs/**",
                                                "/actuator/health/**"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/invoices"
                                        )
                                        .hasAuthority(
                                                "SCOPE_invoices:write"
                                        )

                                        .anyRequest()
                                        .authenticated()
                )
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2
                                        .jwt(
                                                Customizer.withDefaults()
                                        )
                                        .authenticationEntryPoint(
                                                problems
                                        )
                )
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                problems
                                        )
                                        .accessDeniedHandler(
                                                problems
                                        )
                );

        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey
    ) {

        return NimbusJwtEncoder
                .withKeyPair(
                        publicKey,
                        privateKey
                )
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            RSAPublicKey publicKey,
            @Value(
                    "${emitta.security.jwt.issuer}"
            )
            String issuer,
            @Value(
                    "${emitta.security.jwt.audience}"
            )
            String audience
    ) {

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withPublicKey(
                                publicKey
                        )
                        .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators
                        .createDefaultWithIssuer(
                                issuer
                        );

        OAuth2TokenValidator<Jwt> audienceValidator =
                jwt -> {

                    if (
                            jwt.getAudience()
                                    .contains(
                                            audience
                                    )
                    ) {
                        return OAuth2TokenValidatorResult
                                .success();
                    }

                    return OAuth2TokenValidatorResult
                            .failure(
                                    new OAuth2Error(
                                            "invalid_token",
                                            "Required audience is missing",
                                            null
                                    )
                            );
                };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerValidator,
                        audienceValidator
                )
        );

        return decoder;
    }

    @Bean
    RSAPrivateKey jwtPrivateKey(
            @Value(
                    "${emitta.security.jwt.private-key-b64}"
            )
            String encoded
    ) throws Exception {

        byte[] bytes =
                Base64.getDecoder()
                        .decode(
                                encoded
                        );

        return (RSAPrivateKey)
                KeyFactory
                        .getInstance(
                                "RSA"
                        )
                        .generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        bytes
                                )
                        );
    }

    @Bean
    RSAPublicKey jwtPublicKey(
            @Value(
                    "${emitta.security.jwt.public-key-b64}"
            )
            String encoded
    ) throws Exception {

        byte[] bytes =
                Base64.getDecoder()
                        .decode(
                                encoded
                        );

        return (RSAPublicKey)
                KeyFactory
                        .getInstance(
                                "RSA"
                        )
                        .generatePublic(
                                new X509EncodedKeySpec(
                                        bytes
                                )
                        );
    }
}