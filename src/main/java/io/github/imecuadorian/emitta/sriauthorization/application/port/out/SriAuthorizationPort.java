package io.github.imecuadorian.emitta.sriauthorization.application.port.out;

import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationRequest;
import io.github.imecuadorian.emitta.sriauthorization.application.model.SriAuthorizationResult;

public interface SriAuthorizationPort {

    SriAuthorizationResult query(
            SriAuthorizationRequest request
    );
}