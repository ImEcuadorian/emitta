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
import java.util.UUID;

@Configuration
public class SecurityConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
            type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProblemHandler problems,
            TenantAdministrationAccess tenantAccess,
            org.springframework.beans.factory.ObjectProvider<io.github.imecuadorian.emitta.ratelimit.RedisTokenBucket> traffic,
            io.github.imecuadorian.emitta.ratelimit.RateLimitProperties trafficProperties,
            @Value("${emitta.security.trusted-cloudflare-peers:}") String trustedCloudflarePeers
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())
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
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allowsInvoice(authentication.get())))

                                        .requestMatchers(HttpMethod.POST,"/api/v1/fiscal-configuration")
                                        .access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(tenantAccess.allowsFiscalConfiguration(authentication.get(),true)))
                                        .requestMatchers(HttpMethod.GET,"/api/v1/fiscal-configuration")
                                        .access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(tenantAccess.allowsFiscalConfiguration(authentication.get(),false)))
                                        .requestMatchers("/api/v1/tenants/*/fiscal-configuration","/api/v1/tenants/*/documents","/api/v1/tenants/*/documents/*/artifacts/*")
                                        .access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(tenantAccess.allowsPlatformTenant(authentication.get(),context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()))))
                                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/api-clients", "/api/v1/tenants/*/api-clients/*/rotate-secret", "/api/v1/tenants/*/api-clients/*/revoke")
                                        .hasAuthority("SCOPE_platform:admin")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants", "/api/v1/tenants/*/api-clients")
                                        .hasAuthority("SCOPE_platform:admin")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                                        .hasAuthority("SCOPE_platform:admin")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/taxpayers")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allows(authentication.get(), context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()), "taxpayers:write")))
                                        .requestMatchers(HttpMethod.GET, "/api/v1/taxpayers/*/certificates")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allows(authentication.get(), context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()), "certificates:read")))
                                        .requestMatchers(HttpMethod.POST, "/api/v1/taxpayers/*/certificates")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allows(authentication.get(), context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()), "certificates:write")))
                                        .requestMatchers(HttpMethod.POST, "/api/v1/taxpayers/*/establishments")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allows(authentication.get(), context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()), "establishments:write")))
                                        .requestMatchers(HttpMethod.POST, "/api/v1/establishments/*/points-of-issue")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allows(authentication.get(), context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()), "points-of-issue:write")))
                                        .requestMatchers("/actuator/info", "/actuator/metrics/**", "/actuator/prometheus")
                                        .hasAuthority("SCOPE_operations:read")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/documents/*/artifacts/*", "/api/v1/documents/*/ride-job")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allowsRead(authentication.get(), "artifacts:read")))
                                        .requestMatchers(HttpMethod.GET, "/api/v1/documents/*")
                                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                tenantAccess.allowsRead(authentication.get(), "documents:read")))
                                        .anyRequest()
                                        .denyAll()
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

        var limiter=traffic.getIfAvailable();
        if(limiter!=null) {
            http.addFilterBefore(new io.github.imecuadorian.emitta.ratelimit.TrafficFilter(limiter,trafficProperties,false,
                    io.github.imecuadorian.emitta.ratelimit.TrustedClientAddress.peers(trustedCloudflarePeers)),
                    org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
            http.addFilterAfter(new io.github.imecuadorian.emitta.ratelimit.TrafficFilter(limiter,trafficProperties,true),
                    org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        }
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
            org.springframework.jdbc.core.simple.JdbcClient jdbc,
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
                        audienceValidator,
                        jwt -> {
                            try {
                                UUID tenant=UUID.fromString(jwt.getClaimAsString("tenant_id"));
                                boolean active=jdbc.sql("SELECT count(*) FROM emitta.api_clients c JOIN emitta.tenants t ON t.id=c.tenant_id WHERE c.client_id=:client AND c.tenant_id=:tenant AND c.status='ACTIVE' AND t.status='ACTIVE'")
                                        .param("client",jwt.getSubject()).param("tenant",tenant).query(Integer.class).single()==1;
                                if(active) return OAuth2TokenValidatorResult.success();
                            } catch(IllegalArgumentException | NullPointerException invalid) { /* Fail closed. */ }
                            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Inactive API client",null));
                        }
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
