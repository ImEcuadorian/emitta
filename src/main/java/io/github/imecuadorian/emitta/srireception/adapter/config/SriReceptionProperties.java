package io.github.imecuadorian.emitta.srireception.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(
        prefix = "emitta.sri.reception"
)
public record SriReceptionProperties(
        URI testEndpoint,
        URI productionEndpoint,
        Duration connectTimeout,
        Duration requestTimeout
) {

    public SriReceptionProperties {

        Objects.requireNonNull(
                testEndpoint,
                "SRI test reception endpoint cannot be null"
        );

        Objects.requireNonNull(
                productionEndpoint,
                "SRI production reception endpoint cannot be null"
        );

        Objects.requireNonNull(
                connectTimeout,
                "SRI reception connect timeout cannot be null"
        );

        Objects.requireNonNull(
                requestTimeout,
                "SRI reception request timeout cannot be null"
        );

        validateEndpoint(
                testEndpoint,
                "test"
        );

        validateEndpoint(
                productionEndpoint,
                "production"
        );

        if (
                connectTimeout.isZero()
                        || connectTimeout.isNegative()
        ) {

            throw new IllegalArgumentException(
                    "SRI reception connect timeout must be positive"
            );
        }

        if (
                requestTimeout.isZero()
                        || requestTimeout.isNegative()
        ) {

            throw new IllegalArgumentException(
                    "SRI reception request timeout must be positive"
            );
        }
    }

    private static void validateEndpoint(
            URI endpoint,
            String environment
    ) {

        String scheme =
                endpoint.getScheme();

        if (
                scheme == null
                        || (
                        !scheme.equalsIgnoreCase(
                                "http"
                        )
                                && !scheme.equalsIgnoreCase(
                                "https"
                        )
                )
        ) {

            throw new IllegalArgumentException(
                    "SRI "
                            + environment
                            + " reception endpoint must use HTTP or HTTPS"
            );
        }
    }
}