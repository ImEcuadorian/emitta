package io.github.imecuadorian.emitta.fiscalsigning.application.exception;

public class XmlSigningException
        extends RuntimeException {

    public XmlSigningException(
            String message
    ) {
        super(
                message
        );
    }

    public XmlSigningException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}