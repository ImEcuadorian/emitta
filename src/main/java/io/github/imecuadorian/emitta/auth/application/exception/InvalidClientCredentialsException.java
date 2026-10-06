package io.github.imecuadorian.emitta.auth.application.exception;

public final class InvalidClientCredentialsException
        extends RuntimeException {

    public InvalidClientCredentialsException() {
        super(
                "Invalid client credentials"
        );
    }
}