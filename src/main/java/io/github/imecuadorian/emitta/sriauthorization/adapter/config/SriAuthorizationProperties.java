package io.github.imecuadorian.emitta.sriauthorization.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(
        prefix = "emitta.sri.authorization"
)
public record SriAuthorizationProperties(
        URI testEndpoint,
        URI productionEndpoint,
        Duration connectTimeout,
        Duration requestTimeout
) {

    public SriAuthorizationProperties {

        Objects.requireNonNull(
                testEndpoint,
                "SRI authorization test endpoint cannot be null"
        );

        Objects.requireNonNull(
                productionEndpoint,
                "SRI authorization production endpoint cannot be null"
        );

        Objects.requireNonNull(
                connectTimeout,
                "Connect timeout cannot be null"
        );

        Objects.requireNonNull(
                requestTimeout,
                "Request timeout cannot be null"
        );

        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout.isNegative() || requestTimeout.isZero()) {

            throw new IllegalArgumentException(
                    "SRI authorization timeouts must be positive"
            );
        }

        validateEndpoint(testEndpoint);
        validateEndpoint(productionEndpoint);
    }

    private static void validateEndpoint(URI endpoint) {

        String scheme = endpoint.getScheme();

        if (scheme == null
                || (!scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("https"))
                || endpoint.getHost() == null
                || endpoint.getRawQuery() != null
                || endpoint.getFragment() != null) {

            throw new IllegalArgumentException(
                    "Invalid SRI authorization endpoint: " + endpoint
            );
        }
    }
}