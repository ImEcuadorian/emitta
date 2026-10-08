package io.github.imecuadorian.emitta.fiscalsigning.application.exception;

public final class XmlSignatureVerificationException
        extends RuntimeException {

    public XmlSignatureVerificationException(String message) {
        super(message);
    }

    public XmlSignatureVerificationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}