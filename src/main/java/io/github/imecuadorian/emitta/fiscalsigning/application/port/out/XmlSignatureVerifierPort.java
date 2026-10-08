package io.github.imecuadorian.emitta.fiscalsigning.application.port.out;

public interface XmlSignatureVerifierPort {

    void verify(byte[] signedXml);
}