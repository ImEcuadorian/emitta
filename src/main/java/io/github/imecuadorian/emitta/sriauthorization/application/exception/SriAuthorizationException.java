package io.github.imecuadorian.emitta.sriauthorization.application.exception;

public final class SriAuthorizationException
        extends RuntimeException {

    public SriAuthorizationException(String message) {
        super(message);
    }

    public SriAuthorizationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}