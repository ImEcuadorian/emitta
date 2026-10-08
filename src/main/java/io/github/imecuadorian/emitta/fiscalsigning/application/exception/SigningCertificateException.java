package io.github.imecuadorian.emitta.fiscalsigning.application.exception;

public class SigningCertificateException
        extends RuntimeException {

    public SigningCertificateException(
            String message
    ) {

        super(
                message
        );
    }

    public SigningCertificateException(
            String message,
            Throwable cause
    ) {

        super(
                message,
                cause
        );
    }
}