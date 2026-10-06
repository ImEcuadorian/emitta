package io.github.imecuadorian.emitta.auth.adapter.out.security;

import io.github.imecuadorian.emitta.auth.application.port.out.ClientSecretVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

public final class BCryptClientSecretVerifier
        implements ClientSecretVerifier {

    private final PasswordEncoder passwordEncoder;

    public BCryptClientSecretVerifier(
            PasswordEncoder passwordEncoder
    ) {
        this.passwordEncoder =
                Objects.requireNonNull(
                        passwordEncoder
                );
    }

    @Override
    public boolean matches(
            String rawSecret,
            String encodedSecret
    ) {
        return passwordEncoder.matches(
                rawSecret,
                encodedSecret
        );
    }
}