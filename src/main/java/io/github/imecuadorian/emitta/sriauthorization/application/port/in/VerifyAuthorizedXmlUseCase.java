package io.github.imecuadorian.emitta.sriauthorization.application.port.in;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;

public interface VerifyAuthorizedXmlUseCase {

    void verify(
            byte[] originalSignedXml,
            SriAuthorizationResult authorization
    );
}