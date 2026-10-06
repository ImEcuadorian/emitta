package io.github.imecuadorian.emitta.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class JwtTestProperties {

    private static final KeyPair KEY_PAIR =
            generateKeyPair();

    private JwtTestProperties() {
    }

    public static void register(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "emitta.security.jwt.private-key-b64",
                () ->
                        Base64.getEncoder()
                                .encodeToString(
                                        KEY_PAIR
                                                .getPrivate()
                                                .getEncoded()
                                )
        );

        registry.add(
                "emitta.security.jwt.public-key-b64",
                () ->
                        Base64.getEncoder()
                                .encodeToString(
                                        KEY_PAIR
                                                .getPublic()
                                                .getEncoded()
                                )
        );

        registry.add(
                "emitta.security.jwt.issuer",
                () -> "emitta-test"
        );

        registry.add(
                "emitta.security.jwt.audience",
                () -> "emitta-api"
        );

        registry.add(
                "emitta.security.jwt.ttl",
                () -> "PT15M"
        );
    }

    private static KeyPair generateKeyPair() {

        try {

            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance(
                            "RSA"
                    );

            generator.initialize(
                    2048
            );

            return generator
                    .generateKeyPair();

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not generate RSA test key pair",
                    exception
            );
        }
    }
}