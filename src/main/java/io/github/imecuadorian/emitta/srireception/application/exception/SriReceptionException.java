package io.github.imecuadorian.emitta.srireception.application.exception;

public final class SriReceptionException
        extends RuntimeException {

    public SriReceptionException(
            String message
    ) {

        super(
                message
        );
    }

    public SriReceptionException(
            String message,
            Throwable cause
    ) {

        super(
                message,
                cause
        );
    }
}