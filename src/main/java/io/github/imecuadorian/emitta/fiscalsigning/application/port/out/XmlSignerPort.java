package io.github.imecuadorian.emitta.fiscalsigning.application.port.out;

import io.github.imecuadorian.emitta.fiscalsigning.application.model.SignedXml;
import io.github.imecuadorian.emitta.fiscalsigning.application.model.SigningKeyMaterial;

public interface XmlSignerPort {

    SignedXml sign(
            byte[] unsignedXml,
            SigningKeyMaterial keyMaterial
    );
}