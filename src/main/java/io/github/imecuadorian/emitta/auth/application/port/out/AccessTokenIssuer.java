package io.github.imecuadorian.emitta.auth.application.port.out;

import io.github.imecuadorian.emitta.auth.application.model.AuthenticatedApiClient;
import io.github.imecuadorian.emitta.auth.application.model.IssuedAccessToken;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(
            AuthenticatedApiClient client
    );
}