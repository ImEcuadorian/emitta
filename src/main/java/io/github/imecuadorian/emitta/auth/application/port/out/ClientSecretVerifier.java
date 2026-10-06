package io.github.imecuadorian.emitta.auth.application.port.out;

public interface ClientSecretVerifier {

    boolean matches(
            String rawSecret,
            String encodedSecret
    );
}